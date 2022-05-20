package com.xam.bobgame.net;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.utils.ExpoMovingAverage;
import com.xam.bobgame.utils.SequenceNumChecker;

import java.util.Arrays;

/**
 * Manages Packet headers and Packet history.
 */
public class PacketTransport {

    private final NetDriver netDriver;
    final EndPointInfo[] endPointInfos = new EndPointInfo[NetDriver.MAX_CLIENTS];
    private final Array<PacketInfo> droppedPackets = new Array<>();

    public PacketTransport(NetDriver netDriver) {
        this.netDriver = netDriver;
    }

    public PacketInfo setHeaders(Packet packet, Connection connection) {
        // TODO: Avoid search for clientId every time
        int clientId = netDriver.connectionManager.getClientId(connection);
        if (clientId == -1) {
            Log.error("PacketTransport", "No connection with " + connection.getRemoteAddressTCP().getAddress().getHostAddress() + " (" + connection.getID() + ")");
            return null;
        }
        PacketInfo dropped = endPointInfos[clientId].setHeaders(packet, clientId);
        if (dropped != null) {
            synchronized (droppedPackets) {
                droppedPackets.add(dropped);
            }
        }
        return dropped;
    }

    public boolean updateReceived(Packet packet, int clientId) {
        EndPointInfo endPointInfo = endPointInfos[clientId];
        endPointInfo.updateAcks(packet, ((GameEngine) netDriver.getEngine()).getCurrentTime());
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

    public void clear() {
        droppedPackets.clear();
        Arrays.fill(endPointInfos, null);
    }

    void addTransportConnection(int clientId) {
        endPointInfos[clientId] = new EndPointInfo(clientId);
    }

    void removeTransportConnection(int clientId) {
        endPointInfos[clientId] = null;
    }

    void reconnect(int oldClientId, int newClientId) {
        endPointInfos[newClientId].copyTo(endPointInfos[oldClientId]);
        removeTransportConnection(newClientId);
    }

    public class EndPointInfo {
        SequenceNumChecker acks = new SequenceNumChecker(NetDriver.PACKET_SEQUENCE_LIMIT);
        int clientId;

        SequenceNumChecker received = new SequenceNumChecker(NetDriver.PACKET_SEQUENCE_LIMIT);
        int remoteSeqNum = 0;

        PacketInfo[] packetInfos = new PacketInfo[NetDriver.PACKET_SEQUENCE_LIMIT];
        int localSeqNum = 0;

        ExpoMovingAverage roundTripTime = new ExpoMovingAverage(0.1f);

        public EndPointInfo(int clientId) {
            this.clientId = clientId;
            for (int i = 0; i < packetInfos.length; ++i) {
                packetInfos[i] = new PacketInfo();
            }
            acks.setAll();
        }

        PacketInfo setHeaders(Packet packet, int clientId) {
            PacketInfo r = null;
            if (packet.localSeqNum == -1) {
                if (!acks.get(localSeqNum) && packetInfos[localSeqNum].messageCount != -1) {
                    r = Pools.obtain(PacketInfo.class);
                    packetInfos[localSeqNum].copyTo(r);
                }
                // new packet, add to history
                packet.localSeqNum = localSeqNum;
                packet.salt = netDriver.getConnectionManager().getConnectionSlot(clientId).salt;
                packetInfos[localSeqNum].set(packet, clientId, ((GameEngine) netDriver.getEngine()).getCurrentTime());
                acks.unset(localSeqNum);
                localSeqNum = (localSeqNum + 1) % NetDriver.PACKET_SEQUENCE_LIMIT;
            }
            else {
                // debug
                if (packet.type != Packet.PacketType.Disconnect && !packetInfos[localSeqNum].matches(packet)) {
                    Log.warn("Client " + clientId + ": Packet (" + packet + ") doesn't match packet info (" + packetInfos[localSeqNum] + ")");
                }
            }
            packet.remoteSeqNum = remoteSeqNum;
            packet.ack = getAck();
            packet.frameNum = ((GameEngine) netDriver.getEngine()).getCurrentFrame();
//            Log.info("Send Ack: " + DebugUtils.bitString(packet.ack, 32));

            return r;
        }

        void updateAcks(Packet packet, float currentTime) {
            int offset = (packet.remoteSeqNum + NetDriver.PACKET_SEQUENCE_LIMIT - 32) % NetDriver.PACKET_SEQUENCE_LIMIT;
            acks.set(0, offset, 33);
            long bits = acks.getBitMask(offset, 33);
            acks.set(packet.ack | 0x100000000L, offset, 33);
            bits = (bits ^ acks.getBitMask(offset, 33)) & ~bits;
            for (int i = 0; i < 33; ++i) {
                if ((bits & 1) == 1) {
                    int seqNum = (offset + i) % NetDriver.PACKET_SEQUENCE_LIMIT;
                    packetInfos[seqNum].timeAcked = currentTime;
                    roundTripTime.update(currentTime - packetInfos[seqNum].timeSent);
                }
                bits >>>= 1;
            }
        }

        int getAck() {
            return (int) received.getBitMask((received.getHigh() + NetDriver.PACKET_SEQUENCE_LIMIT - 33) % NetDriver.PACKET_SEQUENCE_LIMIT, 32);
        }

        void copyTo(EndPointInfo other) {
            other.acks.set(acks);
            other.remoteSeqNum = remoteSeqNum;
            other.received.set(received);
            other.localSeqNum = localSeqNum;
            for (int i = 0; i < packetInfos.length; ++i) {
                packetInfos[i].copyTo(other.packetInfos[i]);
            }
        }
    }

    public static class PacketInfo {
        int packetSeqNum = -1, clientId = -1;
        int messageCount = -1;
        int[] messageIds = new int[NetDriver.PACKET_MAX_MESSAGES];
        float timeSent = -1;
        float timeAcked = -1;
        Message.MessageType[] messageTypes = new Message.MessageType[NetDriver.PACKET_MAX_MESSAGES];

        void set(Packet packet, int clientId, float currentTime) {
            packetSeqNum = packet.localSeqNum;
            this.clientId = clientId;
            messageCount = packet.messageCount;
            timeSent = currentTime;

            int i;
            for (i = 0; i < messageCount; ++i) {
                messageIds[i] = packet.getMessage(i).messageId;
                messageTypes[i] = packet.getMessage(i).getType();
            }
            while (i < messageIds.length) {
                messageIds[i] = -1;
                messageTypes[i] = null;
                i++;
            }
        }

        void copyTo(PacketInfo other) {
            other.packetSeqNum = packetSeqNum;
            other.clientId = clientId;
            other.timeSent = timeSent;
            other.timeAcked = timeAcked;
            other.messageCount = messageCount;
            System.arraycopy(messageIds, 0, other.messageIds, 0, messageIds.length);
            System.arraycopy(messageTypes, 0, other.messageTypes, 0, messageTypes.length);
        }

        boolean matches(Packet packet) {
            if (messageCount != packet.messageCount) return false;
            for (int i = 0; i < messageCount; ++i) {
                if (messageIds[i] != packet.getMessage(i).messageId) return false;
                if (messageTypes[i] != packet.getMessage(i).getType()) return false;
            }
            return true;
        }

        @Override
        public String toString() {
            return packetSeqNum + " messageCount=" + messageCount + " messages [" + messageIds[0] + ":" + messageTypes[0] + "]";
        }
    }
}
