package com.xam.bobgame.net;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.kryo.KryoException;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProfile;
import com.xam.bobgame.events.*;
import com.xam.bobgame.events.classes.*;
import com.xam.bobgame.game.PlayerInfo;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.utils.BitPacker;
import com.xam.bobgame.utils.Bits2;
import com.xam.bobgame.utils.ExpoMovingAverage;

import java.nio.ByteBuffer;

public class NetDriver extends EntitySystem {
    public static final int DATA_MAX_WORDS = 256;
    public static final int DATA_MAX_SIZE = DATA_MAX_WORDS * 4;
    public static final float BUFFER_TIME_LIMIT = 0.15f;
    public static final int MAX_CLIENTS = 32;
    public static final int MAX_MESSAGE_HISTORY = 256;
    public static final int SERVER_UPDATE_FREQUENCY = 3;
    public static final int CLIENT_UPDATE_FREQUENCY = 1;
    public static final int PORT_TCP = 55192;
    public static final int PORT_UDP = 55196;
    public static final int PACKET_SEQUENCE_LIMIT = 128;
    public static final int JITTER_BUFFER_SIZE = 2;
    public static final int PACKET_MAX_MESSAGES = 7;

    public static final int SNAPSHOT_FRAME_INTERVAL = 60;
    public static final float RECONNECT_FREQUENCY_LIMIT = 1f;

    public static final float INACTIVITY_DISCONNECT_TIMEOUT = 15;

    public static final float RES_POSITION = (float) Math.pow(2d, -12d);
    public static final float RES_ORIENTATION = 1e-4f;
    public static final float RES_VELOCITY = (float) Math.pow(2d, -12d);
    public static final float RES_HOLD_DURATION = (float) Math.pow(2d, -8d);
    public static final float RES_MASS = 1e-4f;
    public static final float RES_COLOR = 1e-4f;
    public static final float MIN_ORIENTATION = -3.14159f;
    public static final float MAX_ORIENTATION = 3.14159f;
    public static final int MAX_ENTITY_ID = (int) Math.pow(2d, 14) - 1;
    public static final float MAX_LATENCY = 2048;
    public static final float RES_LATENCY = (float) Math.pow(2d, -5d);

    public static final float MAX_GRAVITY_STRENGTH = 128;
    public static final float RES_GRAVITY_STRENGTH = 1e-2f;
    public static final float MAX_GRAVITY_RADIUS = 32;
    public static final float RES_GRAVITY_RADIUS = 1e-4f;

    public static final int MIN_SCORE = -512;
    public static final int MAX_SCORE = 511;
    public static final int MAX_SCORE_INCREMENT = 31;

    public static final float RES_MATCH_TIME = 1;
    public static final float MAX_MATCH_TIME = 1800;

    public static final float VEL_SMOOTHING_FACTOR = 1.2f;

    public static final float FRICTION_FACTOR = 2f;
    public static final float RESTITUTION_FACTOR = 0f;
    public static final float DAMPING_FACTOR = 10f;
    public static final float FORCE_FACTOR = 0.4f;

//    private final ExpoMovingAverage simUpdateStepError = new ExpoMovingAverage(0.1f);

    final ConnectionManager connectionManager = new ConnectionManager(this);
    final PacketTransport transport = new PacketTransport(this);
    final NetSerialization serialization = new NetSerialization();
    final MessageReader messageReader = new MessageReader(this);
    final NetServer server = new NetServer(this, serialization);
    final NetClient client = new NetClient(this, serialization);

    /**
     * Events pending to be sent to connected clients. Not guaranteed to be sent in order.
     */
    final Array<ClientEvent> clientEvents = new Array<>(false, 4);

    private final ExpoMovingAverage sendBitrateAverage = new ExpoMovingAverage(0.1f);
    private final ExpoMovingAverage receiveBitrateAverage = new ExpoMovingAverage(0.1f);
    private float sendBitrate = 0;
    private float receiveBitrate = 0;
    private int sentBytes = 0;
    private int receivedBytes = 0;

    public static final Class<?>[] networkEventClasses = {
            PlayerAssignEvent.class,
            PlayerJoinedEvent.class,
            PlayerLeftEvent.class,
            PlayerControlEvent.class,
            PlayerScoreEvent.class,
            PlayerDeathEvent.class,
            EntityCreatedEvent.class,
            EntityDespawnedEvent.class,
            RequestJoinEvent.class,
            MatchEndedEvent.class,
            MatchRestartEvent.class,
    };

    public static int getNetworkEventIndex(Class<? extends NetworkEvent> clazz) {
        for (int i = 0; i < NetDriver.networkEventClasses.length; ++i) {
            if (NetDriver.networkEventClasses[i] == clazz) {
                return i;
            }
        }
        return -1;
    }

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    public NetDriver(int priority) {
        super(priority);

        listeners.put(ClientConnectedEvent.class, new EventListenerAdapter<ClientConnectedEvent>() {
            @Override
            public void handleEvent(ClientConnectedEvent event) {
                GameEngine engine = (GameEngine) getEngine();
                if (engine.getMode() == GameEngine.Mode.Client) {
                    ConnectionManager.ConnectionSlot connectionSlot = connectionManager.getConnectionSlot(client.getHostId());
                    GameProfile.lastConnectedServerAddress = connectionSlot.getAddress();
                    GameProfile.clientSalt = connectionSlot.getSalt();
                    GameProfile.save();
                    engine.resumeSystems();

                    engine.getSystem(EventsSystem.class).queueEvent(Pools.obtain(ConnectionStateRefreshEvent.class));
                }
            }
        });
    }

    @Override
    public void addedToEngine(Engine engine) {
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
        eventsSystem.addListeners(listeners);
        eventsSystem.addListeners(client.listeners);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
        if (eventsSystem != null) {
            eventsSystem.removeListeners(listeners);
            eventsSystem.removeListeners(client.listeners);
        }
        for (ClientEvent clientEvent : clientEvents) Pools.free(clientEvent);
        clientEvents.clear();
        sendBitrateAverage.reset();
        receiveBitrateAverage.reset();
//        connectionManager.clear();
        transport.clearDropped();
    }

    @Override
    public void update(float deltaTime) {
        connectionManager.update(deltaTime);

        RefereeSystem refereeSystem = getEngine().getSystem(RefereeSystem.class);
        for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
            ConnectionManager.ConnectionSlot slot = connectionManager.getConnectionSlot(i);
            PacketTransport.EndPointInfo endPointInfo = transport.endPointInfos[i];
            if (endPointInfo == null || slot == null || slot.playerId == -1) continue;
            PlayerInfo playerInfo = refereeSystem.getPlayerInfo(slot.playerId);
            if (playerInfo == null) continue;
            playerInfo.latency = endPointInfo.roundTripTime.getAverage() * 1000;
        }
//        getEngine().getSystem(PhysicsSystem.class).setSimUpdateStep(PhysicsSystem.SIM_UPDATE_STEP - simUpdateStepError.getAverage());
    }

    /**
     * Called in {@link Engine} update cycle after all other systems have finished updating.
     */
    public void update2(float deltaTime) {
        updateDropped();
        connectionManager.update2();
        updateBitRate(deltaTime);
    }

    private final Bits2 tempBitMask = new Bits2(NetDriver.MAX_CLIENTS);

    public void queueClientEvent(int clientId, NetworkEvent event) {
        queueClientEvent(clientId, event, true);
    }

    public void queueClientEvent(int clientId, NetworkEvent event, boolean copy) {
        synchronized (tempBitMask) {
            if (clientId == -1) {
                tempBitMask.or(connectionManager.getActiveConnectionsMask());
            }
            else {
                tempBitMask.set(clientId);
            }
            queueClientEvent(tempBitMask, event, copy);
            tempBitMask.clear();
        }
    }

    public void queueClientEvent(Bits2 clientMask, NetworkEvent event, boolean copy) {
        if (!connectionManager.hasConnections()) return;
        ClientEvent clientEvent = Pools.obtain(ClientEvent.class);
        NetworkEvent netEvent;
        if (copy) {
            netEvent = Pools.obtain(event.getClass());
            event.copyTo(netEvent);
        }
        else {
            netEvent = event;
        }
        clientEvent.event = netEvent;
        clientMask.copyTo(clientEvent.clientMask);
        synchronized (clientEvents) {
            clientEvents.add(clientEvent);
        }
    }

    private void updateBitRate(float deltaTime) {
        int sent = sentBytes, received = receivedBytes;
        sentBytes = 0;
        receivedBytes = 0;
        sendBitrate = sent * 8 / deltaTime;
        receiveBitrate = received * 8 / deltaTime;
        sendBitrateAverage.update(Float.isNaN(sendBitrate) ? 0 : sendBitrate);
        receiveBitrateAverage.update(Float.isNaN(receiveBitrate) ? 0 : receiveBitrate);
    }

    private void updateDropped() {
        for (PacketTransport.PacketInfo packetInfo : transport.getDroppedPackets()) {
            if (packetInfo.messageCount > 0) {
                for (int i = 0; i < packetInfo.messageCount; ++i) {
                    MessageReader.MessageInfo messageInfo = messageReader.getMessageInfo(packetInfo.messageIds[i]);
                    if (messageInfo == null) continue;
                    switch (messageInfo.type) {
                        case Snapshot:
                            connectionManager.getConnectionSlot(packetInfo.clientId).needsSnapshot = true;
                            Log.warn("Dropped message: " + messageInfo.type);
                            break;
                    }
                }
            }
        }
        transport.clearDropped();
    }

    public void stop() {
        client.stop();
        server.stop();
    }

    public float getBitRate() {
        return sendBitrate;
    }

    public float getAverageSendBitrate() {
        return sendBitrateAverage.getAverage();
    }

    public float getAverageReceiveBitrate() {
        return receiveBitrateAverage.getAverage();
    }

    public boolean startServer() {
        server.start();
        return server.isRunning();
    }

    public void stopServer() {
        server.stop();
    }

    public void setClientReconnect() {
        client.reconnectSalt = GameProfile.clientSalt;
    }

    public void disconnectClient() {
        client.disconnect();
    }

    public boolean canReconnect() {
        return client.canReconnect();
    }

    public boolean connectToServer(String hostAddress) {
        return client.connect(hostAddress);
    }

    public boolean isServerRunning() {
        return ((GameEngine) getEngine()).getMode() == GameEngine.Mode.Server && server.isRunning();
    }

    public boolean isClientConnected() {
        return ((GameEngine) getEngine()).getMode() == GameEngine.Mode.Client && client.isConnected();
    }

    public boolean isClientConnecting() {
        return ((GameEngine) getEngine()).getMode() == GameEngine.Mode.Client && client.isConnecting();
    }

    public int getClientHostId() {
        return client.hostId;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    static class ClientEvent implements Pool.Poolable {
        NetworkEvent event;
        Bits2 clientMask = new Bits2(NetDriver.MAX_CLIENTS);
        Message serializedMessage = new Message(DATA_MAX_SIZE);

        @Override
        public void reset() {
            Pools.free(event);
            event = null;
            clientMask.clear();
            serializedMessage.clear();
        }
    }

    public static abstract class NetworkEvent implements GameEvent, NetSerializable {

        public int clientId = -1;

        public NetworkEvent copyTo(NetworkEvent event) {
            event.clientId = clientId;
            return event;
        }

        @Override
        public void reset() {
            clientId = -1;
        }
    }

    private class NetSerialization extends KryoSerialization {
        Packet returnPacket = new Packet(DATA_MAX_SIZE);
        BitPacker writeBitPacker = new BitPacker();
        BitPacker readBitPacker = new BitPacker();

        @Override
        public void write(Connection connection, ByteBuffer byteBuffer, Object o) {
            int i = byteBuffer.position();
            writeBitPacker.setBuffer(byteBuffer);

            if (o instanceof Packet) {
                Packet packet = (Packet) o;
                if (packet.type == Packet.PacketType.Data && packet.messageCount == 0) {
                    Log.error("Attempted to send empty data packet");
                }
                else {
                    byteBuffer.put((byte) 0xF1);
                    PacketTransport.PacketInfo dropped = transport.setHeaders(packet, connection);
                    packet.encode(writeBitPacker);
//                    if (dropped != null) {
//                        Log.info("Packet dropped: " + dropped.packetSeqNum + " (" + dropped.messageSeqNum + ")");
//                    }
                }
            }
            else {
                byteBuffer.put((byte) 0xF2);
                super.write(connection, byteBuffer, o);
            }

            sentBytes += byteBuffer.position() - i;
        }

        @Override
        public Object read(Connection connection, ByteBuffer byteBuffer) {
            int i = byteBuffer.position();
            Object r = null;
            readBitPacker.setBuffer(byteBuffer);

            byte b = byteBuffer.get();

            if (b == (byte) 0xF1) {
                int clientId = connectionManager.getClientId(connection);
                if (clientId != -1) {
                    if (returnPacket.decode(readBitPacker) != -1) {
//                        Log.debug("Received Packet " + returnPacket);
                        synchronized (transport) {
                            if (!transport.updateReceived(returnPacket, clientId)) {
                                r = returnPacket;
                            }
                        }
                    }
//                    else {
//                        Log.debug("Error decoding Packet: " + DebugUtils.bytesHex(byteBuffer, i, byteBuffer.limit() - i));
//                    }
                }
            }
            else if (b == (byte) 0xF2) {
                try {
                    r = super.read(connection, byteBuffer);
                } catch (KryoException e) {
                    Log.error("NetSerialization.read", "" + e.getClass() + " " + e.getMessage());
                    byteBuffer.position(byteBuffer.limit());
//                    e.printStackTrace();
                }
            }
            else {
                Log.error("NetSerialization.read", "Bad type: " + b);
                byteBuffer.position(byteBuffer.limit());
            }

            receivedBytes += byteBuffer.position() - i;
            return r;
        }
    }
}
