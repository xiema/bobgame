package com.xam.bobgame.net;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.events.*;
import com.xam.bobgame.game.PhysicsSystem;
import com.xam.bobgame.utils.BitPacker;
import com.xam.bobgame.utils.Bits2;
import com.xam.bobgame.utils.DebugUtils;

import java.nio.ByteBuffer;

public class NetDriver extends EntitySystem {
    public static final int DATA_MAX_WORDS = 256;
    public static final int DATA_MAX_SIZE = DATA_MAX_WORDS * 4;
    public static final float BUFFER_TIME_LIMIT = 0.15f;
    public static final int MAX_CLIENTS = 32;
    public static final int MAX_MESSAGE_HISTORY = 256;
    public static final int SERVER_UPDATE_FREQUENCY = 3;
    public static final int PORT_TCP = 55192;
    public static final int PORT_UDP = 55196;
    public static final int PACKET_SEQUENCE_LIMIT = 128;
    public static final int JITTER_BUFFER_SIZE = 4;

    public static final float RES_POSITION = (float) Math.pow(2d, -12d);
//    public static final float RES_POSITION = 1e-4f;
    public static final float RES_ORIENTATION = 1e-4f;
//    public static final float RES_VELOCITY = 1e-4f;
    public static final float RES_VELOCITY = (float) Math.pow(2d, -12d);
    public static final float RES_HOLD_DURATION = (float) Math.pow(2d, -6d);
    public static final float RES_MASS = 1e-4f;
    public static final float RES_COLOR = 1e-4f;
    public static final float MIN_ORIENTATION = -3.14159f;
    public static final float MAX_ORIENTATION = 3.14159f;
    public static final int MAX_ENTITY_ID = 2047;

    public static final int MIN_SCORE = -512;
    public static final int MAX_SCORE = 511;

    public static final float FRICTION_FACTOR = 2f;
    public static final float RESTITUTION_FACTOR = 0f;
    public static final float DAMPING_FACTOR = 10f;
    public static final float FORCE_FACTOR = 0.8f;

    private final DebugUtils.ExpoMovingAverage simUpdateStepError = new DebugUtils.ExpoMovingAverage(0.1f);

    final ConnectionManager connectionManager = new ConnectionManager(this);
    final PacketTransport transport = new PacketTransport(this);
    final NetSerialization serialization = new NetSerialization();
    final MessageReader messageReader = new MessageReader();
    private final NetServer server = new NetServer(this, serialization);
    private final NetClient client = new NetClient(this, serialization);
    final Array<ClientEvent> clientEvents = new Array<>(false, 4);

    private Mode mode = Mode.Client;
    private float curTime = 0;
    private float curTimeDelta = 0;

    private DebugUtils.ExpoMovingAverage movingAverage = new DebugUtils.ExpoMovingAverage(0.1f);
    private float bitrate = 0;
    private int sentBytes = 0;
    private int receivedBytes = 0;

    int counter = 0;

    public static final Class<?>[] networkEventClasses = {
            PlayerAssignEvent.class,
            PlayerJoinedEvent.class,
            PlayerControlEvent.class,
            ScoreBoardUpdateEvent.class,
            EntityCreatedEvent.class,
            PlayerDeathEvent.class,
    };

    public static int getNetworkEventIndex(Class<? extends NetworkEvent> clazz) {
        for (int i = 0; i < NetDriver.networkEventClasses.length; ++i) {
            if (NetDriver.networkEventClasses[i] == clazz) {
                return i;
            }
        }
        return -1;
    }

    public NetDriver() {
        this(0);
    }

    public NetDriver(int priority) {
        super(priority);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        for (ClientEvent clientEvent : clientEvents) Pools.free(clientEvent);
        clientEvents.clear();
        movingAverage.reset();
        curTime = 0;
        connectionManager.clear();
        transport.clearDropped();
    }

    @Override
    public void update(float deltaTime) {
        serialization.clearBits();
        curTime += (curTimeDelta = deltaTime);
        connectionManager.update(deltaTime);
        counter++;

        getEngine().getSystem(PhysicsSystem.class).setSimUpdateStep(PhysicsSystem.SIM_UPDATE_STEP - simUpdateStepError.getAverage());
    }

    public void update2() {
        updateDropped();
        connectionManager.update2();
//        if (server.isRunning()) server.sendEvents();
        updateBitRate(curTimeDelta);
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
        bitrate = sentBytes * 8 / deltaTime;
        if (!Float.isNaN(bitrate)) movingAverage.update(bitrate);
    }

    private void updateDropped() {
        for (PacketTransport.PacketInfo packetInfo : transport.getDroppedPackets()) {
            if (packetInfo.messageId != -1) {
                MessageReader.MessageInfo messageInfo = messageReader.getMessageInfo(packetInfo.messageId);
                switch (messageInfo.type) {
                    case Snapshot:
                        connectionManager.getConnectionSlot(packetInfo.clientId).needsSnapshot = true;
                        Log.warn("Dropped message: " + messageInfo.type);
                        break;
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
        return bitrate;
    }

    public float getAverageBitrate() {
        return movingAverage.getAverage();
    }

    public float getCurTime() {
        return curTime;
    }

    public float getCurTimeDelta() {
        return curTimeDelta;
    }

    public int getCurrentFrame() {
        return ((GameEngine) getEngine()).getCurrentFrame();
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    public NetServer getServer() {
        return mode == Mode.Server ? server : null;
    }

    public NetClient getClient() {
        return mode == Mode.Client ? client : null;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    static class ClientEvent implements Pool.Poolable {
        NetworkEvent event;
        Bits2 clientMask = new Bits2(NetDriver.MAX_CLIENTS);

        @Override
        public void reset() {
            Pools.free(event);
            event = null;
            clientMask.clear();
        }
    }

    public enum Mode {
        Client, Server
    }

    public static class NetworkEvent implements GameEvent {

        protected int readInt(BitPacker builder, int i, int min, int max, boolean write) {
            if (write) {
                builder.packInt(i, min, max);
                return i;
            }
            else {
                return builder.unpackInt(min, max);
            }
        }

        protected float readFloat(BitPacker builder, float f, float min, float max, float res, boolean write) {
            if (write) {
                builder.packFloat(f, min, max, res);
                return f;
            }
            else {
                return builder.unpackFloat(min, max, res);
            }
        }

        protected byte readByte(BitPacker builder, byte b, boolean write) {
            if (write) {
                builder.packByte(b);
                return b;
            }
            else {
                return builder.unpackByte();
            }
        }

        public void read(BitPacker builder, Engine engine, boolean write){

        }

        public NetworkEvent copyTo(NetworkEvent event) {
            return event;
        }

        @Override
        public void reset() {

        }
    }

    private class NetSerialization extends KryoSerialization {
        Packet returnPacket = new Packet(DATA_MAX_SIZE);

        @Override
        public void write(Connection connection, ByteBuffer byteBuffer, Object o) {
            int i = byteBuffer.position();
            if (o instanceof Packet) {
                Packet packet = (Packet) o;
                if (packet.type == Packet.PacketType.Data && packet.getMessage().messageId == -1) {
                    Log.error("Attempted to send data packet with unset messageId ");
                }
                else {
                    byteBuffer.put((byte) 1);
                    PacketTransport.PacketInfo dropped = transport.setHeaders(packet, connection);
                    packet.encode(byteBuffer);
//                    Log.info("Sending Packet " + packet);
//                    if (dropped != null) {
//                        Log.info("Packet dropped: " + dropped.packetSeqNum + " (" + dropped.messageSeqNum + ")");
//                    }
                }
            }
            else {
                byteBuffer.put((byte) 0);
                super.write(connection, byteBuffer, o);
            }
            sentBytes += byteBuffer.position() - i;
        }

        @Override
        public Object read(Connection connection, ByteBuffer byteBuffer) {
            Object r = null;
            int i = byteBuffer.position();
            if (byteBuffer.get() > 0) {
                int clientId = connectionManager.getClientId(connection);
                if (clientId != -1) {
                    if (returnPacket.decode(byteBuffer) != -1) {
            //            Log.info("Received Packet " + returnPacket.localSeqNum + ": " + returnPacket.getMessage());
                        synchronized (transport) {
                            if (!transport.updateReceived(returnPacket, clientId)) {
                                r = returnPacket;
                            }
                        }
                    }
                }
            }
            else {
                r = super.read(connection, byteBuffer);
            }

            receivedBytes += byteBuffer.position() - i;
            return r;
        }

        public void clearBits() {
            sentBytes = 0;
            receivedBytes = 0;
        }
    }

//    public static final int HEADER_WORDS = 2;
//    public static final int SNAPSHOT_MAX_WORDS = 128;
//    public static final int PACKET_MAX_WORDS = DATA_MAX_WORDS + HEADER_WORDS;
//    public static final int SNAPSHOT_MAX_SIZE = SNAPSHOT_MAX_WORDS * 4;
//    public static final int HEADER_SIZE = HEADER_WORDS * 4;
//    public static final int PACKET_MAX_SIZE = PACKET_MAX_WORDS * 4;
}
