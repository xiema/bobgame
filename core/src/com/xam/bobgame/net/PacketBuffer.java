package com.xam.bobgame.net;

import com.xam.bobgame.utils.DebugUtils;

import java.io.InputStream;
import java.nio.ByteBuffer;

public class PacketBuffer{
    private int putIndex = 0;
    private int getIndex = 0;

    private ByteBuffer receiveBuffer = ByteBuffer.allocate(Packet.PACKET_MAX_SIZE);
    private PacketSerializer serializer = new PacketSerializer();

    private final byte[] buffer;
    private final int bufferSize, bufferLength;

    public PacketBuffer(int bufferLength) {
        this.bufferLength = bufferLength;
        this.bufferSize = bufferLength * Packet.PACKET_MAX_SIZE;
        buffer = new byte[bufferSize];
    }

    public int receive(InputStream input) {
        int i = serializer.read(input, receiveBuffer);
        if (i != -1) {
            synchronized (buffer) {
                System.arraycopy(receiveBuffer.array(), 0, buffer, putIndex, Packet.PACKET_MAX_SIZE);
                putIndex = (putIndex + Packet.PACKET_MAX_SIZE) % bufferSize;
                if (putIndex == getIndex) getIndex = (getIndex + Packet.PACKET_MAX_SIZE) % bufferSize;
            }
        }
        receiveBuffer.clear();
        return i;
    }

    public boolean get(ByteBuffer out) {
        if (out.remaining() < Packet.PACKET_MAX_SIZE) {
            DebugUtils.error("PacketBuffer", "Output ByteBuffer has insufficient size.");
            return false;
        }

        synchronized (buffer) {
            if (getIndex == putIndex) return false;
            if (out.hasArray()) {
                System.arraycopy(buffer, getIndex, out.array(), out.position(), Packet.PACKET_MAX_SIZE);
                out.position(out.position() + Packet.PACKET_MAX_SIZE);
            }
            else {
                out.put(buffer, getIndex, Packet.PACKET_MAX_SIZE);
            }
            getIndex = (getIndex + Packet.PACKET_MAX_SIZE) % bufferSize;
        }

        return true;
    }

    public void reset() {
        putIndex = 0;
        getIndex = 0;
    }
}
