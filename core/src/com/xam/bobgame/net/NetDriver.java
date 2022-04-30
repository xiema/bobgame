package com.xam.bobgame.net;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.events.*;
import com.xam.bobgame.utils.DebugUtils;

import java.io.IOException;

public class NetDriver extends EntitySystem {
    public static final int SERVER_UPDATE_FREQUENCY = 3;
    public static final int PORT_TCP = 55192;
    public static final int PORT_UDP = 55196;

    private Mode mode = Mode.Client;

    private ConnectionManager connectionManager = new ConnectionManager(this);

    PacketTransport transport = new PacketTransport(this);
    private NetSerialization serialization = new NetSerialization(this, transport);
    NetServer server = new NetServer(this, serialization);
    NetClient client = new NetClient(this, serialization);
    Array<ClientEvent> clientEvents = new Array<>();

    MessageReader messageReader = new MessageReader(this);
    private Packet eventPacket = new Packet(Net.DATA_MAX_SIZE);
    private Packet updatePacket = new Packet(Net.DATA_MAX_SIZE);
    private Packet snapshotPacket = new Packet(Net.DATA_MAX_SIZE);
    private boolean hasUpdatePacket = false;
    private boolean hasSnapshotPacket = false;

    private DebugUtils.ExpoMovingAverage movingAverage = new DebugUtils.ExpoMovingAverage(0.1f);
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
        serialization.clearBits();
        curTime += (curTimeDelta = deltaTime);
        connectionManager.update(deltaTime);
        if (hasUpdatePacket) {
            hasUpdatePacket = false;
            updatePacket.clear();
        }
        if (hasSnapshotPacket) {
            hasSnapshotPacket = false;
            snapshotPacket.clear();
        }
        t++;
    }

    public void update2() {
        updateDropped();
        connectionManager.update2();
        if (mode == Mode.Server) {
            for (ClientEvent clientEvent : clientEvents) {
                ConnectionManager.ConnectionSlot connectionSlot = connectionManager.getConnectionSlot(clientEvent.clientId);
                messageReader.serializeEvent(eventPacket.getMessage(), clientEvent.event);
//            Log.info("Send event " + sendPacket.getMessage());
                connectionSlot.sendDataPacket(eventPacket);
                eventPacket.clear();
            }
            clientEvents.clear();
        }
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

    public void connect(String host) {
        if (mode != Mode.Client || client.isConnected()) return;
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
        if (mode != Mode.Server || server.isRunning()) return;
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
        connectionManager.clear();
    }

    public void syncClient(ConnectionManager.ConnectionSlot connectionSlot) {
        if (server.getConnections().length == 0) return;

        if (t % SERVER_UPDATE_FREQUENCY == 0) {
            if (connectionSlot.needsSnapshot) {
                if (!hasSnapshotPacket) {
                    messageReader.serialize(snapshotPacket.getMessage(), getEngine(), Message.MessageType.Snapshot, connectionSlot);
                    hasSnapshotPacket = true;
                }
                connectionSlot.needsSnapshot = false;
                connectionSlot.sendDataPacket(snapshotPacket);
//                    Log.info("Send snapshot " + snapshotPacket.getMessage());
            }
            else {
                if (!hasUpdatePacket) {
                    messageReader.serialize(updatePacket.getMessage(), getEngine(), Message.MessageType.Update, null);
                    hasUpdatePacket = true;
                }
                connectionSlot.sendDataPacket(updatePacket);
            }
        }

        updatePacket.clear();
        snapshotPacket.clear();
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
        bitrate = serialization.sentBytes * 8 / deltaTime;
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

    static class ClientEvent implements Pool.Poolable {
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

        public void read(BitPacker builder, boolean write){

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
