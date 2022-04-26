package com.xam.bobgame.net;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.DebugUtils;

import java.nio.ByteBuffer;

public class NetSerialization extends KryoSerialization {

    PacketBuffer packetBuffer = null;
    Packet returnPacket = new Packet(Net.DATA_MAX_SIZE);

    public NetSerialization(PacketBuffer packetBuffer) {
        super();
        setPacketBuffer(packetBuffer);
    }

    public void setPacketBuffer(PacketBuffer packetBuffer) {
        this.packetBuffer = packetBuffer;
    }

    @Override
    public void write(Connection connection, ByteBuffer byteBuffer, Object o) {
        if (o instanceof Packet) {
            byteBuffer.put((byte) 1);
            Packet packet = (Packet) o;
            byteBuffer.putInt((int) packet.getCrc());
            byteBuffer.putInt(packet.getLength());
            packet.copyTo(byteBuffer);
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
        switch (type) {
            case 1:
                int crc = byteBuffer.getInt();
                int length = byteBuffer.getInt();
                returnPacket.set(byteBuffer, length);
                if (crc != ((int) returnPacket.getCrc())) {
                    Log.error("NetSerialization", "Bad CRC [" + length + "]: " + DebugUtils.bytesHex(byteBuffer, i, length));
                    return null;
                }
//                Log.info("[" + length + "] " + returnPacket);
                if (packetBuffer != null) {
                    packetBuffer.receive(returnPacket);
                }
                return returnPacket;
        }
        return super.read(connection, byteBuffer);
    }
}
