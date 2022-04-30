package com.xam.bobgame.net;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.minlog.Log;

import java.nio.ByteBuffer;

public class NetSerialization extends KryoSerialization {

    NetDriver netDriver;
    final PacketTransport transport;
    Packet returnPacket = new Packet(Net.DATA_MAX_SIZE);

    public NetSerialization(NetDriver netDriver, PacketTransport transport) {
        super();
        this.netDriver = netDriver;
        this.transport = transport;
    }

    @Override
    public void write(Connection connection, ByteBuffer byteBuffer, Object o) {
        if (o instanceof Packet) {
            Packet packet = (Packet) o;
            if (packet.type == Packet.PacketType.Data && packet.getMessage().messageId == -1) {
                Log.error("Attempted to send data packet with unset messageId ");
                return;
            }
            byteBuffer.put((byte) 1);
            PacketTransport.PacketInfo dropped = transport.setHeaders(packet, connection);
            packet.encode(byteBuffer);
//            Log.info("Sending Packet " + packet);
            if (dropped != null) {
//                Log.info("Packet dropped: " + dropped.packetSeqNum + " (" + dropped.messageSeqNum + ")");
            }
        }
        else {
            byteBuffer.put((byte) 0);
            super.write(connection, byteBuffer, o);
        }
    }

    @Override
    public Object read(Connection connection, ByteBuffer byteBuffer) {
        if (byteBuffer.get() > 0) {
            if (returnPacket.decode(byteBuffer) == -1) return null;
            returnPacket.clientId = netDriver.getConnectionManager().getClientId(connection);
//            Log.info("Received Packet " + returnPacket.localSeqNum + ": " + returnPacket.getMessage());
            synchronized (transport) {
                if (transport.updateReceived(returnPacket, returnPacket.clientId)) {
                    return null;
                }
            }
            return returnPacket;
        }
        return super.read(connection, byteBuffer);
    }
}
