package com.xam.bobgame.net;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.DebugUtils;
import com.xam.bobgame.utils.SequenceNumChecker;

public class PacketTransport {

    public static final int PACKET_SEQUENCE_LIMIT = 128;

    private NetDriver netDriver;

    private EndPointInfo[] endPointInfos = new EndPointInfo[32];

    private final Array<PacketInfo> droppedPackets = new Array<>();

    public PacketTransport(NetDriver netDriver) {
        this.netDriver = netDriver;
    }

    public void addConnection(int id, int connectionId) {
        endPointInfos[id] = new EndPointInfo(connectionId);
    }

    public PacketInfo setHeaders(Packet packet, int connectionId) {
        packet.connectionId = connectionId;
        PacketInfo dropped = endPointInfos[getEndPointId(connectionId)].setHeaders(packet);
        if (dropped != null) {
            synchronized (droppedPackets) {
                droppedPackets.add(dropped);
            }
        }
        return dropped;
    }

    public boolean updateReceived(Packet packet, int connectionId) {
        EndPointInfo endPointInfo = endPointInfos[getEndPointId(connectionId)];
        endPointInfo.acks.set(packet.ack | 0x100000000L, (packet.remoteSeqNum + PACKET_SEQUENCE_LIMIT - 32) % PACKET_SEQUENCE_LIMIT, 33);
//        Log.info("Rcv Ack: " + packet.remoteSeqNum + " : " + DebugUtils.bitString(packet.ack, 32));
//        Log.info("New Ack: " + endPointInfo.acks);
        boolean b = endPointInfo.received.getAndSet(packet.localSeqNum);
        endPointInfo.remoteSeqNum = endPointInfo.received.gtWrapped(packet.localSeqNum, endPointInfo.remoteSeqNum) ? packet.localSeqNum : endPointInfo.remoteSeqNum;
        return b;
    }

    private int getEndPointId(int connectionId) {
        for (int i = 0; i < endPointInfos.length; ++i) {
            if (endPointInfos[i].connectionId == connectionId) return i;
        }
        return -1;
    }

    public Array<PacketInfo> getDroppedPackets() {
        return droppedPackets;
    }

    public void clearDropped() {
        synchronized (droppedPackets) {
            droppedPackets.clear();
        }
    }

    private static class EndPointInfo {
        SequenceNumChecker acks = new SequenceNumChecker(PACKET_SEQUENCE_LIMIT);
        int connectionId;

        SequenceNumChecker received = new SequenceNumChecker(PACKET_SEQUENCE_LIMIT);
        int remoteSeqNum = 0;

        PacketInfo[] packetInfos = new PacketInfo[PACKET_SEQUENCE_LIMIT];
        int localSeqNum = 0;

        public EndPointInfo(int connectionId) {
            this.connectionId = connectionId;
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
                if (packet.getMessage().messageNum != packetInfos[localSeqNum].messageNum) {
                    Log.warn("Connection " + connectionId + ": Packet " + localSeqNum + " doesn't match message (" + packet.getMessage().messageNum + ", " + packetInfos[localSeqNum].messageNum + ")");
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
        int seqNum = -1, messageNum = -1, connectionId = -1;

        void set(Packet packet) {
            seqNum = packet.localSeqNum;
            messageNum = packet.getMessage().messageNum;
            connectionId = packet.connectionId;
        }

        void copyTo(PacketInfo other) {
            other.seqNum = seqNum;
            other.messageNum = messageNum;
            other.connectionId = connectionId;
        }

        @Override
        public void reset() {
            seqNum = -1;
            messageNum = -1;
            connectionId = -1;
        }
    }
}
