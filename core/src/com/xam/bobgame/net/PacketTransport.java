package com.xam.bobgame.net;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.utils.SequenceNumChecker;

import java.util.Arrays;

public class PacketTransport {

    private NetDriver netDriver;

    private EndPointInfo[] endPointInfos = new EndPointInfo[NetDriver.MAX_CLIENTS];

    private final Array<PacketInfo> droppedPackets = new Array<>();

    public PacketTransport(NetDriver netDriver) {
        this.netDriver = netDriver;
    }

    public PacketInfo setHeaders(Packet packet, Connection connection) {
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
        endPointInfo.acks.set(packet.ack | 0x100000000L, (packet.remoteSeqNum + NetDriver.PACKET_SEQUENCE_LIMIT - 32) % NetDriver.PACKET_SEQUENCE_LIMIT, 33);
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

    private class EndPointInfo {
        SequenceNumChecker acks = new SequenceNumChecker(NetDriver.PACKET_SEQUENCE_LIMIT);
        int clientId;

        SequenceNumChecker received = new SequenceNumChecker(NetDriver.PACKET_SEQUENCE_LIMIT);
        int remoteSeqNum = 0;

        PacketInfo[] packetInfos = new PacketInfo[NetDriver.PACKET_SEQUENCE_LIMIT];
        int localSeqNum = 0;

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
                if (!acks.get(localSeqNum) && packetInfos[localSeqNum].messageId != -1) {
                    r = Pools.obtain(PacketInfo.class);
                    packetInfos[localSeqNum].copyTo(r);
                }
                // new packet, add to history
                packet.localSeqNum = localSeqNum;
                packet.simulationTime = ((GameEngine) netDriver.getEngine()).getSimulationTime();
                packetInfos[localSeqNum].set(packet, clientId);
                acks.unset(localSeqNum);
                localSeqNum = (localSeqNum + 1) % NetDriver.PACKET_SEQUENCE_LIMIT;
            }
            else {
                // debug
                if (packet.type != Packet.PacketType.Disconnect && packet.getMessage().messageId != packetInfos[localSeqNum].messageId) {
                    Log.warn("Client " + clientId + ": Packet " + localSeqNum + " doesn't match message (" + packet.getMessage().messageId + ", " + packetInfos[localSeqNum].messageId + ")");
                }
            }
            packet.remoteSeqNum = remoteSeqNum;
            packet.ack = getAck();
//            Log.info("Send Ack: " + DebugUtils.bitString(packet.ack, 32));

            return r;
        }

        int getAck() {
            return (int) received.getBitMask((received.getHigh() + NetDriver.PACKET_SEQUENCE_LIMIT - 33) % NetDriver.PACKET_SEQUENCE_LIMIT, 32);
        }
    }

    public static class PacketInfo {
        int packetSeqNum = -1, messageId = -1, clientId = -1;

        void set(Packet packet, int clientId) {
            packetSeqNum = packet.localSeqNum;
            messageId = packet.getMessage().messageId;
            this.clientId = clientId;
        }

        void copyTo(PacketInfo other) {
            other.packetSeqNum = packetSeqNum;
            other.messageId = messageId;
            other.clientId = clientId;
        }
    }
}
