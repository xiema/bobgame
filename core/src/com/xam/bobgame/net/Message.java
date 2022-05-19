package com.xam.bobgame.net;

import com.badlogic.gdx.utils.Array;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.BitPacker;
import com.xam.bobgame.utils.DebugUtils;

import java.nio.ByteBuffer;

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
    /**
     * Frame number of game engine when this message was created.
     */
    int frameNum = -1;
    /**
     * The number of entries present in this message. Valid when message is of type Update.
     */
    int entryCount = 0;

    /**
     * For recordkeeping in {@link MessageReader.MessageInfo}.
     */
    Array<Class<? extends NetDriver.NetworkEvent>> eventTypes = new Array<>();

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
        out.frameNum = frameNum;
        out.entryCount = entryCount;
        out.eventTypes.clear();
        out.eventTypes.addAll(eventTypes);
        byteBuffer.rewind();
    }

    /**
     * Appends the contents of another message to this message, adding its entries into this message.
     */
    public void append(Message in) {
        int count = in.length;
        ByteBuffer bufferIn = in.getByteBuffer();
        byteBuffer.limit(length + in.length);
        byteBuffer.position(length);
        while (count-- > 0) {
            byteBuffer.put(bufferIn.get());
        }
        bufferIn.rewind();
        byteBuffer.rewind();
        length += in.length;
        entryCount += in.entryCount;
        eventTypes.addAll(in.eventTypes);

        if (messageId == -1) {
            messageId = in.messageId;
            frameNum = in.frameNum;
            type = in.type;
        }
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
        frameNum = -1;
        entryCount = 0;
        eventTypes.clear();
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

    public String toByteString() {
        return DebugUtils.bytesHex(byteBuffer.array(), 0, length);
    }

    public enum MessageType {
        Update(0), Snapshot(1), Input(2), Empty(3);

        private final int value;

        MessageType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    public enum UpdateType {
        System(0), Event(1);

        private final int value;

        UpdateType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

}
