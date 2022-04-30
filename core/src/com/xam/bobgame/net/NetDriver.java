package com.xam.bobgame.net;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.events.*;
import com.xam.bobgame.utils.BitPacker;
import com.xam.bobgame.utils.DebugUtils;

import java.nio.ByteBuffer;

public class NetDriver extends EntitySystem {
    public static final int DATA_MAX_WORDS = 128;
    public static final int DATA_MAX_SIZE = DATA_MAX_WORDS * 4;
    public static final float BUFFER_TIME_LIMIT = 0.5f;
    public static final int MAX_CLIENTS = 32;
    public static final int MAX_MESSAGE_HISTORY = 256;
    public static final int SERVER_UPDATE_FREQUENCY = 3;
    public static final int PORT_TCP = 55192;
    public static final int PORT_UDP = 55196;
    public static final int PACKET_SEQUENCE_LIMIT = 128;

    public static final float RES_POSITION = 1e-4f;
    public static final float RES_ORIENTATION = 1e-4f;
    public static final float RES_VELOCITY = 1e-4f;
    public static final float RES_MASS = 1e-4f;
    public static final float RES_COLOR = 1e-4f;
    public static final float MAX_ORIENTATION = 3.14159f;


    final ConnectionManager connectionManager = new ConnectionManager(this);
    final PacketTransport transport = new PacketTransport(this);
    final NetSerialization serialization = new NetSerialization();
    final MessageReader messageReader = new MessageReader();
    private final NetServer server = new NetServer(this, serialization);
    private final NetClient client = new NetClient(this, serialization);
    final Array<ClientEvent> clientEvents = new Array<>();

    private Mode mode = Mode.Client;
    private float curTime = 0;
    private float curTimeDelta = 0;

    private DebugUtils.ExpoMovingAverage movingAverage = new DebugUtils.ExpoMovingAverage(0.1f);
    private float bitrate = 0;
    private int sentBytes = 0;
    private int receivedBytes = 0;

    int counter = 0;

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
        counter++;
    }

    public void update2() {
        updateDropped();
        connectionManager.update2();
        if (server.isRunning()) server.sendEvents();
        updateBitRate(curTimeDelta);
    }

    synchronized public void queueClientEvent(int clientId, NetworkEvent event) {
        ClientEvent clientEvent = Pools.obtain(ClientEvent.class);
        clientEvent.event = event;
        clientEvent.clientId = clientId;
        clientEvents.add(clientEvent);
    }

    private void updateBitRate(float deltaTime) {
        bitrate = sentBytes * 8 / deltaTime;
        if (!Float.isNaN(bitrate)) movingAverage.update(bitrate);
    }

    private void updateDropped() {
        for (PacketTransport.PacketInfo packetInfo : transport.getDroppedPackets()) {
            MessageReader.MessageInfo messageInfo = messageReader.getMessageInfo(packetInfo.messageId);
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

    public NetServer getServer() {
        return server;
    }

    public NetClient getClient() {
        return client;
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
                if (returnPacket.decode(byteBuffer) != -1) {
                    int clientId = connectionManager.getClientId(connection);
        //            Log.info("Received Packet " + returnPacket.localSeqNum + ": " + returnPacket.getMessage());
                    synchronized (transport) {
                        if (!transport.updateReceived(returnPacket, clientId)) {
                            r = returnPacket;
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
