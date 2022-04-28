package com.xam.bobgame.net;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.DebugUtils;

import java.nio.ByteBuffer;

public class NetSerialization extends KryoSerialization {

    PacketTransport transport;
    Packet returnPacket = new Packet(Net.DATA_MAX_SIZE);

    public NetSerialization(PacketTransport transport) {
        super();
        this.transport = transport;
    }

    @Override
    public void write(Connection connection, ByteBuffer byteBuffer, Object o) {
        if (o instanceof Packet) {
            Packet packet = (Packet) o;
            PacketTransport.PacketInfo dropped = transport.setHeaders(packet, connection.getID());
            packet.encode(byteBuffer);
//            Log.info("Sending Packet " + packet.localSeqNum + " Message " + packet.getMessage().messageNum);
            if (dropped != null) {
                Log.info("Packet dropped: " + dropped.seqNum + " (" + dropped.messageNum + ")");
            }
        }
        else {
            byteBuffer.put((byte) 0);
            super.write(connection, byteBuffer, o);
        }
    }

    @Override
    public Object read(Connection connection, ByteBuffer byteBuffer) {
        int i = byteBuffer.position();
        byte type = byteBuffer.get();
        if (type > 0) {
            byteBuffer.position(i);
            if (returnPacket.decode(byteBuffer) == -1) return null;
            returnPacket.connectionId = connection.getID();
//            Log.info("Received Packet " + returnPacket.localSeqNum + " Message " + returnPacket.getMessage().messageNum);
            if (transport.updateReceived(returnPacket, connection.getID())) {
                return null;
            }
            return returnPacket;
        }
        return super.read(connection, byteBuffer);
    }
}
