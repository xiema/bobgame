package com.xam.bobgame.net;

import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.DebugUtils;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;

public class Packet {
    private Message message;
    private CRC32 crc32 = new CRC32();
    private long crc = -1;

    int localSeqNum = -1;
    boolean needsAck = false;

    int remoteSeqNum = -1;
    int ack = 0;

    int connectionId = -1;

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

    public long getCrc() {
        if (crc < 0) {
            crc32.reset();
            crc32.update(localSeqNum);
            crc32.update(remoteSeqNum);
            crc32.update(ack);
            crc32.update(message.messageNum);
            crc32.update(message.getLength());
            crc32.update(message.getBytes(), 0, message.getLength());
            crc = crc32.getValue();
        }
        return crc;
    }

    public void encode(ByteBuffer out) {
        out.put((byte) (2 + message.getType().getValue()));
        out.putInt((int) getCrc());
        out.putInt(localSeqNum);
        out.putInt(remoteSeqNum);
        out.putInt(ack);
        out.putInt(message.messageNum);
        out.putInt(message.getLength());
        message.copyTo(out);
    }

    public int decode(ByteBuffer in) {
        clear();

        int i = in.position();
        byte type = in.get();
        float packetCRC = in.getInt();
        localSeqNum = in.getInt();
        remoteSeqNum = in.getInt();
        ack = in.getInt();
        message.messageNum = in.getInt();
        int length = in.getInt();
        message.set(in, length);
        message.setType(Message.MessageType.values()[(type & 0x1)]);

        if (packetCRC != ((int) getCrc())) {
            Log.error("NetSerialization", "Bad CRC [" + length + "]: " + DebugUtils.bytesHex(in, i, length + 13));
            return -1;
        }
        return 0;
    }

    public void copyTo(Packet packet) {
        message.copyTo(packet.message);
        packet.localSeqNum = localSeqNum;
        packet.needsAck = needsAck;
        packet.remoteSeqNum = remoteSeqNum;
        packet.ack = ack;
        packet.connectionId = connectionId;
        packet.crc = crc;
    }

    public boolean equals(Packet other) {
        return message.equals(other.message);
    }

    public void clear() {
        message.clear();
        localSeqNum = -1;
        needsAck = false;
        remoteSeqNum = -1;
        ack = 0;
        connectionId = -1;
        crc = -1;
    }

    @Override
    public String toString() {
        return DebugUtils.intHex(message.messageNum) + DebugUtils.intHex(message.getLength()) + DebugUtils.intHex((int) getCrc()) + DebugUtils.bytesHex(message.getBytes());
    }
}
