package com.xam.bobgame.net;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.kryonet.Client;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.Listener;
import com.esotericsoftware.kryonet.Server;
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

    Server server;
    Client client;
    private IntArray needsSnapshot = new IntArray(false, 4);
    private Array<ClientEvent> clientEvents = new Array<>();

    PacketBuffer updateBuffer = new PacketBuffer(8);
    PacketBuffer eventBuffer = new PacketBuffer(4);
    private MessageReader messageReader = new MessageReader(this);
    private PacketTransport transport = new PacketTransport(this);
    private NetSerialization serialization = new NetSerialization(transport);
    private Packet syncPacket = new Packet(Net.DATA_MAX_SIZE);
    private Packet sendPacket = new Packet(Net.DATA_MAX_SIZE);
    private Packet snapshotPacket = new Packet(Net.SNAPSHOT_MAX_SIZE);


    private MessageInfo[] messageInfos = new MessageInfo[128];
    private SequenceNumChecker messageNumChecker = new SequenceNumChecker(128);
    private int messageCount = 0;

    private IntIntMap playerConnectionMap = new IntIntMap();
    private IntIntMap connectionPlayerMap = new IntIntMap();

    private DebugUtils.ExpoMovingAverage movingAverage = new DebugUtils.ExpoMovingAverage(0.1f);
    private float packetBits = 0;
    private float bitrate = 0;

    private int t = 0;

    public static final Class<?>[] networkEventClasses = {
            PlayerAssignEvent.class,  PlayerControlEvent.class,
    };

    public void setMessageInfo(Message message) {
        message.messageNum = messageCount;
        messageInfos[messageCount % messageInfos.length].set(message);
        messageCount++;
    }

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
        for (int i = 0; i < messageInfos.length; ++i) {
            messageInfos[i] = new MessageInfo();
        }
    }

    @Override
    public void update(float deltaTime) {
        packetBits = 0;
        if (mode == Mode.Client) {
            if (isConnected()) {
                syncWithServer();
            }
            else if (t % 100 == 0) {
                connect("127.0.0.1");
            }
        }
        else {
            if (eventBuffer.get(syncPacket)) {
                messageReader.readEvent(syncPacket.getMessage(), getEngine(), syncPacket.connectionId);
                syncPacket.clear();
            }
        }
        t++;
    }

    public float getAverageBitrate() {
        return movingAverage.getAverage();
    }

    public int getPlayerId(int connectionId) {
        return connectionPlayerMap.get(connectionId, -1);
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

    public void flagSnapshot(int connectionId) {
        needsSnapshot.add(connectionId);
    }

    synchronized public void queueClientEvent(int connectionId, NetworkEvent event) {
        ClientEvent clientEvent = Pools.obtain(ClientEvent.class);
        clientEvent.event = event;
        event.connectionId = connectionId;
        clientEvent.connectionId = connectionId;
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

    public void addPlayerConnection(int playerId, int connectionId) {
        Log.info("New player joined playerId=" + playerId + " connectionId=" + connectionId);
        playerConnectionMap.put(playerId, connectionId);
        connectionPlayerMap.put(connectionId, playerId);
    }

    public void connect(String host) {
        if (mode != Mode.Client || client != null) return;
        client = new Client(8192, 2048, serialization);
        client.start();
        client.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                transport.addConnection(0, connection.getID());
            }
        });
        client.addListener(packetReceiveListener);
        try {
            client.connect(5000, host, PORT_TCP, PORT_UDP);
            Log.info("Connected to " + host);
        } catch (IOException e) {
            client.stop();
            client = null;
            e.printStackTrace();
        }
    }

    private Listener packetReceiveListener = new Listener() {
        @Override
        public void received(Connection connection, Object o) {
            if (o instanceof Packet) {
                Packet packet = (Packet) o;
                Message message = packet.getMessage();

                // message already seen or old and assumed seen
                if (messageNumChecker.getAndSet(message.messageNum)) {
                    Log.info("Discarded message num=" + message.messageNum);
                    return;
                }

                if (packet.getMessage().getType() == Message.MessageType.Event)  {
                    eventBuffer.receive(packet);
                }
                else {
                    updateBuffer.receive(packet);
                }
            }
        }
    };

    public void startServer() {
        if (mode != Mode.Server || server != null) return;
        server = new Server(8192, 2048, serialization);
        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                if (getEngine() == null) return;
                EventsSystem eventsSystem = getEngine().getSystem(EventsSystem.class);
                if (eventsSystem == null) return;
                transport.addConnection(0, connection.getID());
                ClientConnectedEvent event = Pools.obtain(ClientConnectedEvent.class);
                event.connectionId = connection.getID();
                eventsSystem.queueEvent(event);
//                needsSnapshot.add(connection.getID());
            }
        });
        server.addListener(packetReceiveListener);
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
            messageReader.serialize(sendPacket.getMessage(), getEngine(), Message.MessageType.Update);
        packetBits = 0;
//        DebugUtils.log("Packet", DebugUtils.bytesHex(sendBuffer.array()));
        boolean snapshot = false;

        for (Connection connection : server.getConnections()) {
            if (needsSnapshot.contains(connection.getID())) {
                if (!snapshot) {
                    messageReader.serialize(snapshotPacket.getMessage(), getEngine(), Message.MessageType.Snapshot);
                    snapshot = true;
                }
                needsSnapshot.removeValue(connection.getID());
                snapshotPacket.connectionId = connection.getID();
                connection.sendUDP(snapshotPacket);
                packetBits += snapshotPacket.getBitSize();
            }
            else {
                if (t % SERVER_UPDATE_FREQUENCY == 0) {
                    sendPacket.connectionId = connection.getID();
                    connection.sendUDP(sendPacket);
    //                Log.info("Update", "[" + sendPacket.getLength() + "] " + sendPacket);
                    packetBits += sendPacket.getBitSize();
                }
            }
        }
//        server.sendToAllUDP(sendPacket);

        sendPacket.clear();
        snapshotPacket.clear();

        for (ClientEvent clientEvent : clientEvents) {
            sendPacket.connectionId = clientEvent.connectionId;
            messageReader.serializeEvent(sendPacket.getMessage(), clientEvent.event);
            for (Connection connection : server.getConnections()) {
                if (connection.getID() == clientEvent.connectionId) {
                    connection.sendUDP(sendPacket);
                }
            }
            packetBits += sendPacket.getBitSize();
            sendPacket.clear();
        }
        clientEvents.clear();
    }

    public void syncWithServer() {
        if (mode != Mode.Client) return;
        if (eventBuffer.get(syncPacket)) {
            messageReader.readEvent(syncPacket.getMessage(), getEngine(), 0);
        }
        boolean sent = false;
        syncPacket.clear();

        if (updateBuffer.get(syncPacket)) {
//            Log.info("[" + syncPacket.getLength() + "] " + syncPacket);
            messageReader.deserialize(syncPacket.getMessage(), getEngine());
        }
        syncPacket.clear();

        for (ClientEvent clientEvent : clientEvents) {
            messageReader.serializeEvent(sendPacket.getMessage(), clientEvent.event);
            client.sendUDP(sendPacket);
            packetBits += sendPacket.getBitSize();
            sendPacket.clear();
            sent = true;
        }
        clientEvents.clear();

        if (!sent) {
            messageReader.serialize(sendPacket.getMessage(), getEngine(), Message.MessageType.Empty);
            client.sendUDP(sendPacket);
            packetBits += sendPacket.getBitSize();
            sendPacket.clear();
        }
    }

    public void updateDropped() {
        for (PacketTransport.PacketInfo packetInfo : transport.getDroppedPackets()) {
            MessageInfo messageInfo = messageInfos[packetInfo.messageNum];
            Log.warn("Dropped message: " + messageInfo.type);
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

    public Server getServer() {
        return server;
    }

    private static class ClientEvent implements Pool.Poolable {
        NetworkEvent event;
        int connectionId;

        @Override
        public void reset() {
            Pools.free(event);
            event = null;
            connectionId = -1;
        }
    }

    public enum Mode {
        Client, Server
    }

    public static class NetworkEvent implements GameEvent {

        NetDriver netDriver = null;
        int connectionId = -1;

        protected int getPlayerId() {
            int id = netDriver.getPlayerId(connectionId);
            return id;
        }

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
            event.connectionId = connectionId;
            return event;
        }

        @Override
        public void reset() {
            netDriver = null;
            connectionId = -1;
        }
    }
}
