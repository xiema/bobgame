package com.xam.bobgame.net;

import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.BitPacker;
import com.xam.bobgame.utils.DebugUtils;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

public class Packet {
    PacketType type;

    private Message message;
    private CRC32 crc32 = new CRC32();
    private long crc = -1;

    int localSeqNum = -1;

    int remoteSeqNum = -1;
    int ack = 0;

    float simulationTime = 0;

    private final BitPacker bitPacker = new BitPacker();

    public Packet(int size) {
        message = new Message(size);
    }

    public Message getMessage() {
        return message;
    }

    public PacketType getType() {
        return type;
    }

    public long getCrc() {
        if (crc < 0) {
            crc32.reset();
            crc32.update(localSeqNum);
            crc32.update(remoteSeqNum);
            crc32.update(ack);
            crc32.update(type.getValue());
            crc32.update(Float.floatToRawIntBits(simulationTime));
            crc32.update(message.messageId);
            crc32.update(message.getType().getValue());
            crc32.update(message.getLength());
            crc32.update(message.getBytes(), 0, message.getLength());
            crc = crc32.getValue();
        }
        return crc;
    }

    public void encode(ByteBuffer out) {
        bitPacker.setBuffer(out);

        bitPacker.packInt((int) getCrc());
        bitPacker.packInt(localSeqNum, 0, NetDriver.PACKET_SEQUENCE_LIMIT);
        bitPacker.packInt(remoteSeqNum, 0, NetDriver.PACKET_SEQUENCE_LIMIT);
        bitPacker.packInt(ack);
        bitPacker.packInt(type.getValue(), 0, PacketType.values().length-1);
        bitPacker.packFloat(simulationTime);
        bitPacker.packInt(message.messageId);
        bitPacker.packInt(message.frameNum);
        bitPacker.packInt(message.getType().getValue(), 0, Message.MessageType.values().length-1);
        bitPacker.packInt(message.entryCount, 0, 15);
        bitPacker.packInt(message.getLength(), 0, NetDriver.DATA_MAX_SIZE);
        bitPacker.padToLong();
        message.copyTo(bitPacker);
        bitPacker.packByte((byte) 0xFF);
        bitPacker.flush(false);
    }

    public int decode(ByteBuffer in) {
        int i, length;
        clear();
        bitPacker.setBuffer(in);

        i = in.position();
        float packetCRC = bitPacker.unpackInt();
        localSeqNum = bitPacker.unpackInt(0, NetDriver.PACKET_SEQUENCE_LIMIT);
        remoteSeqNum = bitPacker.unpackInt(0, NetDriver.PACKET_SEQUENCE_LIMIT);
        ack = bitPacker.unpackInt();
        type = PacketType.values()[bitPacker.unpackInt(0, PacketType.values().length-1)];

        simulationTime = bitPacker.unpackFloat();
        message.messageId = bitPacker.unpackInt();
        message.frameNum = bitPacker.unpackInt();
        message.setType(Message.MessageType.values()[bitPacker.unpackInt(0, Message.MessageType.values().length-1)]);
        message.entryCount = bitPacker.unpackInt(0, 15);
        length = bitPacker.unpackInt(0, NetDriver.DATA_MAX_SIZE);
        bitPacker.skipToLong();
        message.set(bitPacker, length);

        byte footer = bitPacker.unpackByte();
        if (footer != (byte) 0xFF) {
            Log.error("Wrong footer " + footer);
            return -1;
        }

        if (packetCRC != ((int) getCrc())) {
            Log.error("Packet", "Bad CRC [" + length + "]: " + DebugUtils.bytesHex(in, i, length + 13));
            return -1;
        }
        return 0;
    }

    public void copyTo(Packet packet) {
        packet.type = type;
        message.copyTo(packet.message);
        packet.localSeqNum = localSeqNum;
        packet.remoteSeqNum = remoteSeqNum;
        packet.ack = ack;
        packet.simulationTime = simulationTime;
        packet.crc = crc;
    }

    public boolean equals(Packet other) {
        return message.equals(other.message);
    }

    public void clear() {
        type = null;
        message.clear();
        localSeqNum = -1;
        remoteSeqNum = -1;
        ack = 0;
        simulationTime = 0;
        crc = -1;
    }

    @Override
    public String toString() {
//        return DebugUtils.intHex(message.messageId) + DebugUtils.intHex(message.getLength()) + DebugUtils.intHex((int) getCrc()) + DebugUtils.bytesHex(message.getBytes());
        if (type == PacketType.Data) return "[Packet " + localSeqNum + "] " + message.getType() + " entryCount=" + message.entryCount;
        else return "[Packet " + localSeqNum + "] " + type;
    }

    public enum PacketType {
        ConnectionRequest(0), ConnectionChallenge(1), ConnectionChallengeResponse(2),
        Data(3), Disconnect(4),;

        private final int value;

        PacketType(int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }
    }
}
