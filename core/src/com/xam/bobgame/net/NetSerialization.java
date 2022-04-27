package com.xam.bobgame.net;

import com.esotericsoftware.kryonet.Connection;
import com.esotericsoftware.kryonet.KryoSerialization;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.DebugUtils;

import java.nio.ByteBuffer;

public class NetSerialization extends KryoSerialization {

    PacketBuffer eventBuffer = null;
    PacketBuffer updateBuffer = null;
    Packet returnPacket = new Packet(Net.DATA_MAX_SIZE);

    public NetSerialization(PacketBuffer updateBuffer, PacketBuffer eventBuffer) {
        super();
        setPacketBuffer(updateBuffer, eventBuffer);
    }

    public void setPacketBuffer(PacketBuffer updateBuffer, PacketBuffer eventBuffer) {
        this.updateBuffer = updateBuffer;
        this.eventBuffer = eventBuffer;
    }

    @Override
    public void write(Connection connection, ByteBuffer byteBuffer, Object o) {
        if (o instanceof Packet) {
            Packet packet = (Packet) o;
            byteBuffer.put((byte) (2 + packet.getType().getValue()));
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
        if (type > 0) {
            int crc = byteBuffer.getInt();
            int length = byteBuffer.getInt();
            returnPacket.set(byteBuffer, length);
            Packet.PacketType packetType = Packet.PacketType.values()[(type & 0x1)];
            returnPacket.setType(packetType);
            if (crc != ((int) returnPacket.getCrc())) {
                Log.error("NetSerialization", "Bad CRC [" + length + "]: " + DebugUtils.bytesHex(byteBuffer, i, length));
                return null;
            }
//                Log.info("[" + length + "] " + returnPacket);
            returnPacket.connectionId = connection.getID();
            if (packetType == Packet.PacketType.Normal)  {
                if (updateBuffer != null) {
                    updateBuffer.receive(returnPacket);
                }
            }
            else {
                if (eventBuffer != null) {
                    eventBuffer.receive(returnPacket);
                }
            }
            return returnPacket;

        }
        return super.read(connection, byteBuffer);
    }
}
