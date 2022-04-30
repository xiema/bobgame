package com.xam.bobgame.net;

import com.esotericsoftware.minlog.Log;
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

    int clientId = -1;

    public Packet(int size) {
        message = new Message(size);
    }

    public int getBitSize() {
        // crc, localSeqNum, remoteSeqNum, ack, messageNum, messageLength, payload
        return (6 * 4 + message.getLength()) * 8;
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
            crc32.update(message.messageId);
            crc32.update(message.getType().getValue());
            crc32.update(message.getLength());
            crc32.update(message.getBytes(), 0, message.getLength());
            crc = crc32.getValue();
        }
        return crc;
    }

    public void encode(ByteBuffer out) {
        out.putInt((int) getCrc());
        out.putInt(localSeqNum);
        out.putInt(remoteSeqNum);
        out.putInt(ack);
        out.putInt(type.getValue());
        out.putInt(message.messageId);
        out.putInt(message.getType().getValue());
        out.putInt(message.getLength());
        message.copyTo(out);
    }

    public int decode(ByteBuffer in) {
        int i, length;
        clear();

        i = in.position();
        float packetCRC = in.getInt();
        localSeqNum = in.getInt();
        remoteSeqNum = in.getInt();
        ack = in.getInt();
        type = PacketType.values()[in.getInt()];
        message.messageId = in.getInt();
        message.setType(Message.MessageType.values()[in.getInt()]);
        length = in.getInt();
        message.set(in, length);

        if (packetCRC != ((int) getCrc())) {
            Log.error("NetSerialization", "Bad CRC [" + length + "]: " + DebugUtils.bytesHex(in, i, length + 13));
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
        packet.clientId = clientId;
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
        clientId = -1;
        crc = -1;
    }

    @Override
    public String toString() {
//        return DebugUtils.intHex(message.messageId) + DebugUtils.intHex(message.getLength()) + DebugUtils.intHex((int) getCrc()) + DebugUtils.bytesHex(message.getBytes());
        if (type == PacketType.Data) return "[Packet " + localSeqNum + "] " + message;
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
