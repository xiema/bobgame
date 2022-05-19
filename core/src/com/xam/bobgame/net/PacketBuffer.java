package com.xam.bobgame.net;

import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;

/**
 * A buffer for Packets. Packets are kept for a time period before they can be retrieved. Retrieved packets are
 * guaranteed to be in order, but some packets may be skipped.
 */
public class PacketBuffer{
    private final NetDriver netDriver;

    private int oldestReceivedIndex = 0;

    private int putIndex = 0;
    private int getIndex = 0;

    private final Packet[] buffer;
    private final boolean[] bufferFlag;
    private final float[] receiveTime;
    private final int bufferLength, halfBufferLength;

    private float timeLimit;

    public PacketBuffer(NetDriver netDriver, int bufferLength, float timeLimit) {
        this.netDriver = netDriver;
        this.bufferLength = bufferLength;
        this.timeLimit = timeLimit;

        buffer = new Packet[bufferLength];
        for (int i = 0; i < bufferLength; ++i) {
            buffer[i] = new Packet(NetDriver.DATA_MAX_SIZE);
        }
        bufferFlag = new boolean[bufferLength];
        receiveTime = new float[bufferLength];
        halfBufferLength = bufferLength / 2;
    }

    private boolean gtWrapped(int i, int j) {
        return (i < j && j - i > halfBufferLength ) || (i > j && i - j < halfBufferLength);
    }

    /**
     * Adds a packet to the buffer.
     */
    public void receive(Packet packet) {
        synchronized (buffer) {
            int i = packet.localSeqNum % bufferLength;
//            Log.info("Receive: [" + packet.getMessage().getLength() + "] " + packet.getMessage());
            packet.copyTo(buffer[i]);
            bufferFlag[i] = true;
            receiveTime[i] = ((GameEngine) netDriver.getEngine()).getCurrentTime();
            boolean b = false;
            if (i == putIndex || gtWrapped(i, putIndex)) {
                while (putIndex != i) {
                    bufferFlag[putIndex] = false;
                    if (putIndex == getIndex) b = true;
                    putIndex = (putIndex + 1) % bufferLength;
                }
                putIndex = (putIndex + 1) % bufferLength;
                if (b) {
                    Log.info("receive: Skipping packets " + getIndex + "-" + putIndex);
                    getIndex = oldestReceivedIndex = (putIndex + 1) % bufferLength;
                }
                while (!bufferFlag[oldestReceivedIndex] && oldestReceivedIndex != putIndex) {
                    oldestReceivedIndex = (oldestReceivedIndex + 1) % bufferLength;
                }
            }
            else {
                if (i < oldestReceivedIndex) {
                    oldestReceivedIndex = i;
                }
            }
        }
    }

    /**
     * Attempts to retrieve a Packet from the buffer, returning true if successful.
     */
    public boolean get(Packet out) {
        boolean retrieved = false;

        synchronized (buffer) {
            if (getIndex == putIndex) return false;
            if (bufferFlag[getIndex]) {
                buffer[getIndex].copyTo(out);
                bufferFlag[getIndex] = false;
                getIndex = (getIndex + 1) % bufferLength;
                retrieved = true;
            }
            else if (bufferFlag[oldestReceivedIndex] && ((GameEngine) netDriver.getEngine()).getCurrentTime() - receiveTime[oldestReceivedIndex] > timeLimit) {
                buffer[oldestReceivedIndex].copyTo(out);
                bufferFlag[oldestReceivedIndex] = false;
                Log.info("get: Skipping packets " + getIndex + "-" + oldestReceivedIndex);
                getIndex = oldestReceivedIndex = (oldestReceivedIndex + 1) % bufferLength;
                retrieved = true;
            }
            while (!bufferFlag[oldestReceivedIndex] && oldestReceivedIndex != putIndex) {
                oldestReceivedIndex = (oldestReceivedIndex + 1) % bufferLength;
            }
        }

        return retrieved;
    }

    public void reset() {
        putIndex = 0;
        getIndex = 0;
    }

    public void debug(String tag) {
        Log.info(tag + ": putIndex=" + putIndex + " getIndex=" + getIndex + " oldestReceivedIndex" + oldestReceivedIndex);
    }
}
