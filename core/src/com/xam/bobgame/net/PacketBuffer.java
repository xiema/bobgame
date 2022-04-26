package com.xam.bobgame.net;

import com.xam.bobgame.utils.DebugUtils;

import java.io.InputStream;
import java.nio.ByteBuffer;

public class PacketBuffer{
    private int putIndex = 0;
    private int getIndex = 0;
    private int jump = 0;

    private ByteBuffer receiveBuffer = ByteBuffer.allocate(Net.PACKET_MAX_SIZE);
    private PacketSerializer serializer = new PacketSerializer();

    private final byte[] buffer;
    private final int bufferSize, bufferLength;

    public PacketBuffer(int bufferLength) {
        this.bufferLength = bufferLength;
        this.bufferSize = bufferLength * Net.PACKET_MAX_SIZE;
        buffer = new byte[bufferSize];
    }

    public int receive(InputStream input) {
        int i = serializer.read(input, receiveBuffer);
        if (i != -1) {
            synchronized (buffer) {
                System.arraycopy(receiveBuffer.array(), 0, buffer, putIndex, Net.PACKET_MAX_SIZE);
                putIndex = (putIndex + Net.PACKET_MAX_SIZE) % bufferSize;
                if (putIndex == getIndex) {
                    getIndex = (getIndex + Net.PACKET_MAX_SIZE) % bufferSize;
                    jump += 1;
                }
            }
        }
        receiveBuffer.clear();
        return i;
    }

    public int receive(ByteBuffer byteBuffer) {
        int crcHash = byteBuffer.getInt();
        if (!serializer.checkCRC(crcHash, byteBuffer)) {
            DebugUtils.error("PacketBuffer", "Bad CRC:");
            return -1;
        }
        byteBuffer.rewind();
        int length = byteBuffer.remaining();
        synchronized (buffer) {
            int nextIndex = (putIndex + Net.PACKET_MAX_SIZE) % bufferSize;
            while (byteBuffer.hasRemaining()) {
                buffer[putIndex++] = byteBuffer.get();
            }
//            System.arraycopy(byteBuffer.array(), byteBuffer.position(), buffer, putIndex, byteBuffer.remaining());
            putIndex = nextIndex;
            if (putIndex == getIndex) {
                getIndex = (getIndex + Net.PACKET_MAX_SIZE) % bufferSize;
                jump += 1;
            }
        }

        return length;
    }

    public boolean get(ByteBuffer out) {
        if (out.remaining() < Net.PACKET_MAX_SIZE) {
            DebugUtils.error("PacketBuffer", "Output ByteBuffer has insufficient size.");
            return false;
        }

        synchronized (buffer) {
            if (getIndex == putIndex) return false;
            if (out.hasArray()) {
                System.arraycopy(buffer, getIndex, out.array(), out.position(), Net.PACKET_MAX_SIZE);
                out.position(out.position() + Net.PACKET_MAX_SIZE);
            }
            else {
                out.put(buffer, getIndex, Net.PACKET_MAX_SIZE);
            }
            getIndex = (getIndex + (jump + 1) * Net.PACKET_MAX_SIZE) % bufferSize;
            jump /= 2;
        }

        return true;
    }

    public void reset() {
        putIndex = 0;
        getIndex = 0;
    }
}
