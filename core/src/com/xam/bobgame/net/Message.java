package com.xam.bobgame.net;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.DebugUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class Message {
    private ByteBuffer byteBuffer;

    /**
     * Length in bytes
     */
    private int length = 0;

    private MessageType type = MessageType.Empty;

    /**
     * Unique identifier for message. Does not vary across clients.
     */
    int messageId = -1;

    public Message(int size) {
        byteBuffer = ByteBuffer.allocate(size);
    }

    public Message(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    public int getLength() {
        return length;
    }

    public void setLength(int length) {
        if (length > byteBuffer.array().length) {
            Log.warn("Message", "Attempt to set length to " + length + ", but buffer is only " + byteBuffer.array().length);
            this.length = byteBuffer.array().length;
        }
        else {
            this.length = length;
        }
    }

    public byte[] getBytes() {
        return byteBuffer.array();
    }

    public ByteBuffer getByteBuffer() {
        return byteBuffer;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public MessageType getType() {
        return type;
    }

    public void copyTo(ByteBuffer out) {
        int i = length;
        while (i-- > 0) {
            out.put(byteBuffer.get());
        }
        byteBuffer.rewind();
    }

    public void copyTo(BitPacker bitPacker) {
        bitPacker.packBytes(byteBuffer, length);
        byteBuffer.rewind();
    }

    public void copyTo(Message out) {
        out.set(byteBuffer, length);
        out.type = type;
        out.messageId = messageId;
        byteBuffer.rewind();
    }

    public void set(ByteBuffer in, int length) {
        byteBuffer.clear();
        this.length = length;
        while (length-- > 0) {
            byteBuffer.put(in.get());
        }
        byteBuffer.flip();
    }

    public void set(BitPacker bitPacker, int length) {
        byteBuffer.clear();
        this.length = length;
        bitPacker.unpackBytes(byteBuffer, length);
        byteBuffer.flip();
    }

    public void clear() {
        byteBuffer.clear();
        length = 0;
        type = MessageType.Empty;
        messageId = -1;
//        needsAck = false;
    }

    public boolean equals(Message other) {
        for (int i = 0; i < length; ++i) {
            if (byteBuffer.get(i) != other.byteBuffer.get(i)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "<" + messageId + "> " + type;
    }

    public enum MessageType {
        Update(0), Event(1), Snapshot(2), Empty(3)
        ;

        private final int value;

        MessageType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

}
