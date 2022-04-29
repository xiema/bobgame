package com.xam.bobgame.net;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.SequenceNumChecker;

public class PacketTransport {

    public static final int PACKET_SEQUENCE_LIMIT = 128;

    private NetDriver netDriver;

    private EndPointInfo[] endPointInfos = new EndPointInfo[ConnectionManager.MAX_CLIENTS];

    private final Array<PacketInfo> droppedPackets = new Array<>();

    public PacketTransport(NetDriver netDriver) {
        this.netDriver = netDriver;
    }

    public PacketInfo setHeaders(Packet packet, Connection connection) {
        packet.clientId = netDriver.getConnectionManager().getClientId(connection);
        PacketInfo dropped = endPointInfos[packet.clientId].setHeaders(packet);
        if (dropped != null) {
            synchronized (droppedPackets) {
                droppedPackets.add(dropped);
            }
        }
        return dropped;
    }

    public boolean updateReceived(Packet packet, int clientId) {
        EndPointInfo endPointInfo = endPointInfos[clientId];
        endPointInfo.acks.set(packet.ack | 0x100000000L, (packet.remoteSeqNum + PACKET_SEQUENCE_LIMIT - 32) % PACKET_SEQUENCE_LIMIT, 33);
//        Log.info("Rcv Ack: " + packet.remoteSeqNum + " : " + DebugUtils.bitString(packet.ack, 32));
//        Log.info("New Ack: " + endPointInfo.acks);
        boolean b = endPointInfo.received.getAndSet(packet.localSeqNum);
        endPointInfo.remoteSeqNum = endPointInfo.received.gtWrapped(packet.localSeqNum, endPointInfo.remoteSeqNum) ? packet.localSeqNum : endPointInfo.remoteSeqNum;
        return b;
    }

    public Array<PacketInfo> getDroppedPackets() {
        return droppedPackets;
    }

    public void clearDropped() {
        synchronized (droppedPackets) {
            droppedPackets.clear();
        }
    }

    void addTransportConnection(int clientId) {
        endPointInfos[clientId] = new EndPointInfo(clientId);
    }

    void removeTransportConnection(int clientId) {
        Pools.free(endPointInfos[clientId]);
        endPointInfos[clientId] = null;
    }

    private static class EndPointInfo {
        SequenceNumChecker acks = new SequenceNumChecker(PACKET_SEQUENCE_LIMIT);
        int clientId;

        SequenceNumChecker received = new SequenceNumChecker(PACKET_SEQUENCE_LIMIT);
        int remoteSeqNum = 0;

        PacketInfo[] packetInfos = new PacketInfo[PACKET_SEQUENCE_LIMIT];
        int localSeqNum = 0;

        public EndPointInfo(int clientId) {
            this.clientId = clientId;
            for (int i = 0; i < packetInfos.length; ++i) {
                packetInfos[i] = Pools.obtain(PacketInfo.class);
            }
            acks.setAll();
        }

        PacketInfo setHeaders(Packet packet) {
            PacketInfo r = null;
            if (packet.localSeqNum == -1) {
                if (!acks.get(localSeqNum)) {
                    r = Pools.obtain(PacketInfo.class);
                    packetInfos[localSeqNum].copyTo(r);
                }
                // new packet, add to history
                packet.localSeqNum = localSeqNum;
                packetInfos[localSeqNum].set(packet);
                acks.unset(localSeqNum);
                localSeqNum = (localSeqNum + 1) % PACKET_SEQUENCE_LIMIT;
            }
            else {
                // debug
                if (packet.getMessage().messageId != packetInfos[localSeqNum].messageId) {
                    Log.warn("Client " + clientId + ": Packet " + localSeqNum + " doesn't match message (" + packet.getMessage().messageId + ", " + packetInfos[localSeqNum].messageId + ")");
                }
            }
            packet.remoteSeqNum = remoteSeqNum;
            packet.ack = getAck();
//            Log.info("Send Ack: " + DebugUtils.bitString(packet.ack, 32));

            return r;
        }

        int getAck() {
            return (int) received.getBitMask((received.getHigh() + PACKET_SEQUENCE_LIMIT - 33) % PACKET_SEQUENCE_LIMIT, 32);
        }
    }

    public static class PacketInfo implements Pool.Poolable {
        int packetSeqNum = -1, messageId = -1, clientId = -1;

        void set(Packet packet) {
            packetSeqNum = packet.localSeqNum;
            messageId = packet.getMessage().messageId;
            clientId = packet.clientId;
        }

        void copyTo(PacketInfo other) {
            other.packetSeqNum = packetSeqNum;
            other.messageId = messageId;
            other.clientId = clientId;
        }

        @Override
        public void reset() {
            packetSeqNum = -1;
            messageId = -1;
            clientId = -1;
        }
    }
}
