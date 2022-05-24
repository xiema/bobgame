package com.xam.bobgame.net;

import com.badlogic.gdx.utils.Null;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.BitPacker;

import java.util.zip.CRC32;

public class Packet {
    PacketType type;

    private Message[] messages = new Message[NetDriver.PACKET_MAX_MESSAGES];
    private CRC32 crc32 = new CRC32();
    private long crc = -1;

    int localSeqNum = -1;

    int messageCount = 0;
    int remoteSeqNum = -1;
    int ack = 0;
    int salt = 0;

    /**
     * Frame number of game engine when this packet was sent.
     */
    int frameNum = -1;

    boolean requestSnapshot = false;

    public Packet(int size) {
        for (int i = 0; i < messages.length; ++i) messages[i] = new Message(size);
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
            crc32.update(salt);
            crc32.update(requestSnapshot ? 1 : 0);

            for (int i = 0; i < messageCount; ++i) {
                Message message = messages[i];
                crc32.update(message.messageId);
                crc32.update(message.getType().getValue());
                crc32.update(message.getLength());
                crc32.update(message.getBytes(), 0, message.getLength());
            }

            crc = crc32.getValue();
        }
        return crc;
    }

    public void encode(BitPacker bitPacker) {
        bitPacker.packInt((int) getCrc());
        bitPacker.packInt(localSeqNum, 0, NetDriver.PACKET_SEQUENCE_LIMIT - 1);
        bitPacker.packInt(remoteSeqNum, 0, NetDriver.PACKET_SEQUENCE_LIMIT - 1);
        bitPacker.packInt(ack);
        bitPacker.packInt(type.getValue(), 0, PacketType.values().length-1);
        bitPacker.packInt(salt);
        bitPacker.packInt(requestSnapshot ? 1 : 0, 0, 1);
        bitPacker.packInt(frameNum);

        bitPacker.packInt(messageCount, 0, NetDriver.PACKET_MAX_MESSAGES);
        for (int i = 0; i < messageCount; ++i) {
            Message message = messages[i];
            bitPacker.packInt(message.messageId);
            bitPacker.packInt(message.frameNum);
            bitPacker.packInt(message.getType().getValue(), 0, Message.MessageType.values().length-1);
            bitPacker.packInt(message.getLength(), 0, NetDriver.DATA_MAX_SIZE);
            bitPacker.padToWord();
            message.copyTo(bitPacker);
        }
        bitPacker.padToWord();

        bitPacker.packInt(0xFFFFFFFF);
        bitPacker.flush(false);
    }

    public int decode(BitPacker bitPacker) {
        int totalLength = 0;
        clear();

        float packetCRC = bitPacker.unpackInt();
        localSeqNum = bitPacker.unpackInt(0, NetDriver.PACKET_SEQUENCE_LIMIT - 1);
        remoteSeqNum = bitPacker.unpackInt(0, NetDriver.PACKET_SEQUENCE_LIMIT - 1);
        ack = bitPacker.unpackInt();
        type = PacketType.values()[bitPacker.unpackInt(0, PacketType.values().length-1)];
        salt = bitPacker.unpackInt();
        requestSnapshot = bitPacker.unpackInt(0, 1) == 1;
        frameNum = bitPacker.unpackInt();

        messageCount = bitPacker.unpackInt(0, NetDriver.PACKET_MAX_MESSAGES);
        for (int i = 0; i < messageCount; ++i) {
            Message message = messages[i];
            message.messageId = bitPacker.unpackInt();
            message.frameNum = bitPacker.unpackInt();
            message.setType(Message.MessageType.values()[bitPacker.unpackInt(0, Message.MessageType.values().length-1)]);
            int length = bitPacker.unpackInt(0, NetDriver.DATA_MAX_SIZE);
            bitPacker.skipToWord();
            message.set(bitPacker, length);
        }
        bitPacker.skipToWord();

        int footer = bitPacker.unpackInt();
        if (footer != 0xFFFFFFFF) {
            Log.error("Wrong footer " + footer);
            return -1;
        }

        if (packetCRC != ((int) getCrc())) {
            Log.error("Packet", "Bad CRC [" + totalLength + "] (" + messageCount + ")");
            return -1;
        }
        return 0;
    }

    public @Null Message createMessage() {
        if (messageCount >= messages.length) {
            Log.debug("Packet.createMessage", "Reached message limit");
            return null;
        }
        return messages[messageCount++];
    }

    public boolean isFull() {
        return messageCount >= messages.length;
    }

    public boolean addMessage(Message in) {
        if (messageCount >= messages.length) {
            Log.debug("Packet.addMessage", "Reached message limit");
            return false;
        }
        in.copyTo(messages[messageCount++]);
        return true;
    }

    public Message getMessage(int index) {
        if (index >= messageCount) Log.error("Invalid message index " + index + " (" + messageCount + ")");
        return messages[index];
    }

    public int getMessageCount() {
        return messageCount;
    }

    public void copyTo(Packet packet) {
        packet.type = type;
        packet.messageCount = messageCount;
        for (int i = 0; i < messageCount; ++i) messages[i].copyTo(packet.messages[i]);
//        message.copyTo(packet.message);
        packet.localSeqNum = localSeqNum;
        packet.remoteSeqNum = remoteSeqNum;
        packet.ack = ack;
        packet.salt = salt;
        packet.crc = crc;
        packet.requestSnapshot = requestSnapshot;
        packet.frameNum = frameNum;
    }

//    public boolean equals(Packet other) {
//        return message.equals(other.message);
//    }

    public void clear() {
        type = null;
//        message.clear();
        for (Message message : messages) message.clear();
        messageCount = 0;
        localSeqNum = -1;
        remoteSeqNum = -1;
        ack = 0;
        salt = 0;
        crc = -1;
        requestSnapshot = false;
        frameNum = -1;
    }

    @Override
    public String toString() {
//        return DebugUtils.intHex(message.messageId) + DebugUtils.intHex(message.getLength()) + DebugUtils.intHex((int) getCrc()) + DebugUtils.bytesHex(message.getBytes());
        if (type == PacketType.Data) return "[Data Packet " + localSeqNum + "] (" + messageCount + ") " + messages[0].getType() + "...";
        else return "[" + type + " Packet " + localSeqNum + "] ";
    }

    public enum PacketType {
        ConnectionRequest(0), ConnectionChallenge(1), ConnectionChallengeResponse(2),
        Data(3), Disconnect(4), Reconnect(5), Empty(6);

        private final int value;

        PacketType(int value) {
            this.value = value;
        }

        int getValue() {
            return value;
        }
    }
}
