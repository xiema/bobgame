package com.xam.bobgame.net;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.minlog.Log;

import java.nio.ByteBuffer;

public class NetSerialization extends KryoSerialization {

    NetDriver netDriver;
    final PacketTransport transport;
    Packet returnPacket = new Packet(Net.DATA_MAX_SIZE);

    int sentBytes = 0;
    int receivedBytes = 0;

    public NetSerialization(NetDriver netDriver, PacketTransport transport) {
        super();
        this.netDriver = netDriver;
        this.transport = transport;
    }

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
    //            Log.info("Sending Packet " + packet);
                if (dropped != null) {
    //                Log.info("Packet dropped: " + dropped.packetSeqNum + " (" + dropped.messageSeqNum + ")");
                }
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
                returnPacket.clientId = netDriver.getConnectionManager().getClientId(connection);
    //            Log.info("Received Packet " + returnPacket.localSeqNum + ": " + returnPacket.getMessage());
                synchronized (transport) {
                    if (!transport.updateReceived(returnPacket, returnPacket.clientId)) {
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
