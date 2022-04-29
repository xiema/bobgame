package com.xam.bobgame.net;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.events.*;
import com.xam.bobgame.utils.DebugUtils;
import com.xam.bobgame.utils.SequenceNumChecker;

import java.io.IOException;

public class NetDriver extends EntitySystem {
    public static final int SERVER_UPDATE_FREQUENCY = 3;
    public static final int PORT_TCP = 55192;
    public static final int PORT_UDP = 55196;

    private Mode mode = Mode.Client;

    private ConnectionManager connectionManager = new ConnectionManager(this);

    NetServer server;
    Client client;
    private Array<ClientEvent> clientEvents = new Array<>();

    PacketBuffer updateBuffer = new PacketBuffer(this, 8);
//    PacketBuffer eventBuffer = new PacketBuffer(this, 4);
    private MessageReader messageReader = new MessageReader(this);
    private PacketTransport transport = new PacketTransport(this);
    private NetSerialization serialization = new NetSerialization(this, transport);
    private Packet syncPacket = new Packet(Net.DATA_MAX_SIZE);
    private Packet sendPacket = new Packet(Net.DATA_MAX_SIZE);
    private Packet snapshotPacket = new Packet(Net.SNAPSHOT_MAX_SIZE);
    final SequenceNumChecker messageNumChecker = new SequenceNumChecker(128);

//    private IntIntMap playerConnectionMap = new IntIntMap();
//    private IntIntMap connectionPlayerMap = new IntIntMap();

    private DebugUtils.ExpoMovingAverage movingAverage = new DebugUtils.ExpoMovingAverage(0.1f);
    private float packetBits = 0;
    private float bitrate = 0;

    private int t = 0;
    private float curTime = 0;
    private float curTimeDelta = 0;

    public static final Class<?>[] networkEventClasses = {
            PlayerAssignEvent.class,  PlayerControlEvent.class,
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
    public void update(float deltaTime) {
        packetBits = 0;
        curTime += (curTimeDelta = deltaTime);
        if (mode == Mode.Client) {
            if (isConnected()) {
                syncWithServer();
            }
            else if (t % 100 == 0) {
                connect("127.0.0.1");
            }
        }
        else {
//            eventBuffer.debug("eventBuffer");
            while (updateBuffer.get(syncPacket)) {
                if (syncPacket.getMessage().getType() == Message.MessageType.Event) {
                    messageReader.readEvent(syncPacket.getMessage(), getEngine(), syncPacket.clientId);
                }
                else {
                    messageReader.deserialize(syncPacket.getMessage(), getEngine());
                }
                syncPacket.clear();
            }
        }
        t++;
    }

    public void flagSnapshot(int clientId) {
        connectionManager.getConnectionSlot(clientId).needsSnapshot = true;
    }

    synchronized public void queueClientEvent(int clientId, NetworkEvent event) {
        ClientEvent clientEvent = Pools.obtain(ClientEvent.class);
        clientEvent.event = event;
        clientEvent.clientId = clientId;
        clientEvents.add(clientEvent);
    }

//    synchronized NetworkEvent sendClientEvent(int connectionId, NetworkEvent event) {
//        packetReader.serializeEvent(sendPacket, event);
//        for (Connection connection : server.getConnections()) {
//            if (connection.getID() == connectionId) {
//                connection.sendUDP(sendPacket);
//            }
//        }
//        sendPacket.clear();
//    }

    public void connect(String host) {
        if (mode != Mode.Client || client != null) return;
        client = new NetClient(this, serialization);
        client.start();
        try {
            client.connect(5000, host, PORT_TCP, PORT_UDP);
            Log.info("Connected to " + host);
        } catch (IOException e) {
            client.stop();
            client = null;
            e.printStackTrace();
        }
    }

    public void startServer() {
        if (mode != Mode.Server || server != null) return;
        server = new NetServer(this, serialization);
        server.start();
        try {
            server.bind(PORT_TCP, PORT_UDP);
            Log.info("Server listening on " + PORT_TCP + "/" + PORT_UDP);
        } catch (IOException e) {
            server.stop();
            server = null;
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
        if (client != null) {
            client.stop();
            client = null;
        }
        updateBuffer.reset();
    }

    public void syncClients(float deltaTime) {
        if (server.getConnections().length == 0) return;

        if (t % SERVER_UPDATE_FREQUENCY == 0)
            messageReader.serialize(sendPacket.getMessage(), getEngine(), Message.MessageType.Update, null);
        packetBits = 0;
//        DebugUtils.log("Packet", DebugUtils.bytesHex(sendBuffer.array()));
        boolean snapshot = false;

        if (t % SERVER_UPDATE_FREQUENCY == 0) {
            for (int clientId = 0; clientId < ConnectionManager.MAX_CLIENTS; ++clientId) {
                ConnectionManager.ConnectionSlot connectionSlot = connectionManager.getConnectionSlot(clientId);
                if (connectionSlot == null) continue;

                if (connectionSlot.needsSnapshot) {
                    if (!snapshot) {
                        messageReader.serialize(snapshotPacket.getMessage(), getEngine(), Message.MessageType.Snapshot, connectionSlot);
                        snapshot = true;
                    }
                    connectionSlot.needsSnapshot = false;
                    connectionSlot.sendDataPacket(snapshotPacket);
//                    Log.info("Send snapshot " + snapshotPacket.getMessage());
                    packetBits += snapshotPacket.getBitSize();
                }
                else {
                    connectionSlot.sendDataPacket(sendPacket);
                    packetBits += sendPacket.getBitSize();
                }
            }
        }

        sendPacket.clear();
        snapshotPacket.clear();

        for (ClientEvent clientEvent : clientEvents) {
            ConnectionManager.ConnectionSlot connectionSlot = connectionManager.getConnectionSlot(clientEvent.clientId);
            messageReader.serializeEvent(sendPacket.getMessage(), clientEvent.event);
//            Log.info("Send event " + sendPacket.getMessage());
            connectionSlot.sendDataPacket(sendPacket);
            packetBits += sendPacket.getBitSize();
            sendPacket.clear();
        }
        clientEvents.clear();
    }

    public void syncWithServer() {
        if (mode != Mode.Client) return;
//        while (eventBuffer.get(syncPacket)) {
//            messageReader.readEvent(syncPacket.getMessage(), getEngine(), 0);
//            syncPacket.clear();
//        }

        while (updateBuffer.get(syncPacket)) {
//            Log.info("[" + syncPacket.getLength() + "] " + syncPacket);
            if (syncPacket.getMessage().getType() == Message.MessageType.Event) {
                messageReader.readEvent(syncPacket.getMessage(), getEngine(), 0);
            }
            else {
                messageReader.deserialize(syncPacket.getMessage(), getEngine());
            }
            syncPacket.clear();
        }

        boolean sent = false;
        if (connectionManager.getHostClientId() != -1) {
            ConnectionManager.ConnectionSlot connectionSlot = connectionManager.getHostConnectionSlot();

            for (ClientEvent clientEvent : clientEvents) {
                messageReader.serializeEvent(sendPacket.getMessage(), clientEvent.event);
//                Log.info("Send event " + sendPacket.getMessage());
                connectionSlot.sendDataPacket(sendPacket);
                packetBits += sendPacket.getBitSize();
                sendPacket.clear();
                sent = true;
            }
            clientEvents.clear();

            if (!sent) {
                messageReader.serialize(sendPacket.getMessage(), getEngine(), Message.MessageType.Empty, connectionManager.getHostConnectionSlot());
                connectionSlot.sendDataPacket(sendPacket);
                packetBits += sendPacket.getBitSize();
                sendPacket.clear();
            }
        }
    }

    public void updateDropped() {
        for (PacketTransport.PacketInfo packetInfo : transport.getDroppedPackets()) {
            MessageInfo messageInfo = messageReader.getMessageInfo(packetInfo.messageId);
            switch (messageInfo.type) {
                case Snapshot:
                    connectionManager.getConnectionSlot(packetInfo.clientId).needsSnapshot = true;
                case Event:
                    Log.warn("Dropped message: " + messageInfo.type);
                    break;
            }
        }
        transport.clearDropped();
    }

    public float updateBitRate(float deltaTime) {
        bitrate = packetBits / deltaTime;
        if (Float.isNaN(bitrate)) return movingAverage.getAverage();
        return movingAverage.update(bitrate);
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

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public Mode getMode() {
        return mode;
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    public NetServer getServer() {
        return server;
    }

    public PacketTransport getTransport() {
        return transport;
    }

    public ConnectionManager getConnectionManager() {
        return connectionManager;
    }

    private static class ClientEvent implements Pool.Poolable {
        NetworkEvent event;
        int clientId;

        @Override
        public void reset() {
            Pools.free(event);
            event = null;
            clientId = -1;
        }
    }

    public enum Mode {
        Client, Server
    }

    public static class NetworkEvent implements GameEvent {

        NetDriver netDriver = null;

        protected int readInt(Message.MessageBuilder builder, int i, int min, int max, boolean write) {
            if (write) {
                builder.packInt(i, min, max);
                return i;
            }
            else {
                return builder.unpackInt(min, max);
            }
        }

        protected float readFloat(Message.MessageBuilder builder, float f, float min, float max, float res, boolean write) {
            if (write) {
                builder.packFloat(f, min, max, res);
                return f;
            }
            else {
                return builder.unpackFloat(min, max, res);
            }
        }

        protected byte readByte(Message.MessageBuilder builder, byte b, boolean write) {
            if (write) {
                builder.packByte(b);
                return b;
            }
            else {
                return builder.unpackByte();
            }
        }

        public void read(Message.MessageBuilder builder, boolean write){

        }

        public NetworkEvent copyTo(NetworkEvent event) {
            event.netDriver = netDriver;
            return event;
        }

        @Override
        public void reset() {
            netDriver = null;
        }
    }
}
