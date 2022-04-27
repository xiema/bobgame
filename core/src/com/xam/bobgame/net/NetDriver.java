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

import java.io.IOException;

public class NetDriver extends EntitySystem {
    public static final int PORT_TCP = 55192;
    public static final int PORT_UDP = 55196;
    private Mode mode = Mode.Client;

    Server server;
    Client client;
    private IntArray needsSnapshot = new IntArray(false, 4);
    private Array<ClientEvent> clientEvents = new Array<>();

    PacketBuffer updateBuffer = new PacketBuffer(8);
    PacketBuffer eventBuffer = new PacketBuffer(4);
    private PacketReader packetReader = new PacketReader(this);
    private NetSerialization serialization = new NetSerialization(updateBuffer, eventBuffer);
    private Packet syncPacket = new Packet(Net.DATA_MAX_SIZE);
    private Packet sendPacket = new Packet(Net.DATA_MAX_SIZE);
    private Packet snapshotPacket = new Packet(Net.SNAPSHOT_MAX_SIZE);

    private IntIntMap playerConnectionMap = new IntIntMap();
    private IntIntMap connectionPlayerMap = new IntIntMap();

    private DebugUtils.ExpoMovingAverage movingAverage = new DebugUtils.ExpoMovingAverage(0.1f);
    private float packetBits = 0;
    private float bitrate = 0;

    private int t = 0;

    public static final Class<?>[] networkEventClasses = {
            PlayerAssignEvent.class,  PlayerControlEvent.class,
    };

    public NetDriver() {
        this(0);
    }

    public NetDriver(int priority) {
        super(priority);
    }

    @Override
    public void update(float deltaTime) {
        if (mode == Mode.Client) {
            if (isConnected()) {
                syncWithServer();
            }
            else if (t++ % 100 == 0) {
                connect("127.0.0.1");
            }
        }
        else {
            if (eventBuffer.get(syncPacket)) {
                getEngine().getSystem(EventsSystem.class).queueEvent(packetReader.readEvent(syncPacket));
                syncPacket.clear();
            }
        }
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
        server = new Server(8192, 2048, serialization);
        server.addListener(new Listener() {
            @Override
            public void connected(Connection connection) {
                ClientConnectedEvent event = Pools.obtain(ClientConnectedEvent.class);
                event.connectionId = connection.getID();
                getEngine().getSystem(EventsSystem.class).queueEvent(event);
//                needsSnapshot.add(connection.getID());
            }
        });
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

        packetReader.serialize(sendPacket, getEngine(), 1);
        packetBits = 0;
//        DebugUtils.log("Packet", DebugUtils.bytesHex(sendBuffer.array()));
        boolean snapshot = false;

        for (Connection connection : server.getConnections()) {
            if (needsSnapshot.contains(connection.getID())) {
                if (!snapshot) {
                    packetReader.serialize(snapshotPacket, getEngine(), 2);
                    snapshot = true;
                }
                needsSnapshot.removeValue(connection.getID());
//                Log.info("Snapshot: [" + snapshotPacket.getLength() + "] " + snapshotPacket);
                connection.sendUDP(snapshotPacket);
                packetBits += snapshotPacket.getLength() * 8;
            }
            else {
                connection.sendUDP(sendPacket);
//                Log.info("Update", "[" + sendPacket.getLength() + "] " + sendPacket);
                packetBits += sendPacket.getLength() * 8;
            }
        }
//        server.sendToAllUDP(sendPacket);

        sendPacket.clear();
        snapshotPacket.clear();

        for (ClientEvent clientEvent : clientEvents) {
            packetReader.serializeEvent(sendPacket, clientEvent.event);
            for (Connection connection : server.getConnections()) {
                if (connection.getID() == clientEvent.connectionId) {
                    connection.sendUDP(sendPacket);
                }
            }
        }
        clientEvents.clear();

        bitrate = packetBits / deltaTime;
        movingAverage.update(bitrate);
    }

    public void syncWithServer() {
        if (mode != Mode.Client) return;
        if (eventBuffer.get(syncPacket)) {
            getEngine().getSystem(EventsSystem.class).queueEvent(packetReader.readEvent(syncPacket));
        }
        syncPacket.clear();
        if (updateBuffer.get(syncPacket)) {
//            Log.info("[" + syncPacket.getLength() + "] " + syncPacket);
            packetReader.syncEngine(syncPacket, getEngine());
        }
        syncPacket.clear();

        for (ClientEvent clientEvent : clientEvents) {
            packetReader.serializeEvent(sendPacket, clientEvent.event);
            client.sendUDP(sendPacket);
            sendPacket.clear();
        }
        clientEvents.clear();
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

        protected int readInt(Packet.PacketBuilder builder, int i, int min, int max, boolean write) {
            if (write) {
                builder.packInt(i, min, max);
                return i;
            }
            else {
                return builder.unpackInt(min, max);
            }
        }

        protected float readFloat(Packet.PacketBuilder builder, float f, float min, float max, float res, boolean write) {
            if (write) {
                builder.packFloat(f, min, max, res);
                return f;
            }
            else {
                return builder.unpackFloat(min, max, res);
            }
        }

        protected byte readByte(Packet.PacketBuilder builder, byte b, boolean write) {
            if (write) {
                builder.packByte(b);
                return b;
            }
            else {
                return builder.unpackByte();
            }
        }

        public void read(Packet.PacketBuilder builder, boolean write){

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
