package com.xam.bobgame.net;

import com.esotericsoftware.minlog.Log;

public class PacketBuffer{
    private int putIndex = 0;
    private int getIndex = 0;
    private int jump = 0;

    private final Packet[] buffer;
    private final int bufferLength;

    public PacketBuffer(int bufferLength) {
        this.bufferLength = bufferLength;
        buffer = new Packet[bufferLength];
        for (int i = 0; i < bufferLength; ++i) {
            buffer[i] = new Packet(Net.DATA_MAX_SIZE);
        }
    }

    public int receive(Packet packet) {
        synchronized (buffer) {
//            Log.info("Receive: [" + packet.getMessage().getLength() + "] " + packet.getMessage());
            packet.copyTo(buffer[putIndex]);
//            System.arraycopy(byteBuffer.array(), byteBuffer.position(), buffer, putIndex, byteBuffer.remaining());
            putIndex = (putIndex + 1) % bufferLength;
            if (putIndex == getIndex) {
                getIndex = (getIndex + 1) % bufferLength;
                jump += 1;
            }
        }

        return packet.getMessage().getLength();
    }

    public boolean get(Packet out) {
        synchronized (buffer) {
            if (getIndex == putIndex) return false;
            buffer[getIndex].copyTo(out);
            getIndex = (getIndex + jump + 1) % bufferLength;
            jump /= 2;
        }

        return true;
    }

    public void reset() {
        putIndex = 0;
        getIndex = 0;
    }

    public void debug(String tag) {
        Log.info(tag + ": p=" + putIndex + " g=" + getIndex + " j=" + jump);
    }
}
