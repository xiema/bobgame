package com.xam.bobgame.net;

import com.esotericsoftware.minlog.Log;

public class PacketBuffer{
    private final NetDriver netDriver;

    private int oldestReceivedIndex = 0;

    private int putIndex = 0;
    private int getIndex = 0;
    private int jump = 0;

    private final Packet[] buffer;
    private final boolean[] bufferFlag;
    private final float[] receiveTime;
    private final int bufferLength, halfBufferLength;

    private int frameDelay = 0;
    private int remoteFrameNum = 0;
//    private float simulationDelay = 0;
//    private float remoteSimulationTime = 0;

    int frameOffset = 0;

    public PacketBuffer(NetDriver netDriver, int bufferLength) {
        this.netDriver = netDriver;
        this.bufferLength = bufferLength;
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

    public int receive(Packet packet) {
        synchronized (buffer) {
            int i = packet.localSeqNum % bufferLength;
//            Log.info("Receive: [" + packet.getMessage().getLength() + "] " + packet.getMessage());
            packet.copyTo(buffer[i]);
            bufferFlag[i] = true;
            receiveTime[i] = netDriver.getCurTime();
//            System.arraycopy(byteBuffer.array(), byteBuffer.position(), buffer, putIndex, byteBuffer.remaining());
//            putIndex = (putIndex + 1) % bufferLength;
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

//        remoteSimulationTime = Math.max(remoteSimulationTime, packet.simulationTime);

        return packet.getMessage().getLength();
    }

    public boolean get(Packet out) {
        boolean b = false;

        synchronized (buffer) {
            if (getIndex == putIndex) return false;
            if (bufferFlag[getIndex]) {
//            if (bufferFlag[getIndex] && (buffer[getIndex].type != Packet.PacketType.Data || remoteFrameNum - buffer[getIndex].frameNum >= frameDelay)) {
//            if (bufferFlag[getIndex] && (buffer[getIndex].type != Packet.PacketType.Data || remoteSimulationTime + netDriver.getCurTime() - receiveTime[getIndex] - buffer[getIndex].simulationTime >= simulationDelay)) {
//            if (bufferFlag[getIndex] && (buffer[getIndex].type != Packet.PacketType.Data || netDriver.getCurrentFrame() + frameOffset >= buffer[getIndex].frameNum)) {
                buffer[getIndex].copyTo(out);
                bufferFlag[getIndex] = false;
                getIndex = (getIndex + 1) % bufferLength;
                b = true;
            }
            else if (bufferFlag[oldestReceivedIndex] && netDriver.getCurTime() - receiveTime[oldestReceivedIndex] > NetDriver.BUFFER_TIME_LIMIT) {
//            else if (bufferFlag[oldestReceivedIndex] && (buffer[oldestReceivedIndex].type != Packet.PacketType.Data || remoteFrameNum - buffer[oldestReceivedIndex].frameNum >= frameDelay)) {
//            else if (bufferFlag[oldestReceivedIndex] && (buffer[oldestReceivedIndex].type != Packet.PacketType.Data || remoteSimulationTime + netDriver.getCurTime() - receiveTime[oldestReceivedIndex] - buffer[oldestReceivedIndex].simulationTime >= simulationDelay)) {
//            else if (bufferFlag[oldestReceivedIndex] && (buffer[oldestReceivedIndex].type != Packet.PacketType.Data || netDriver.getCurrentFrame() + frameOffset >= buffer[oldestReceivedIndex].frameNum)) {
                buffer[oldestReceivedIndex].copyTo(out);
                bufferFlag[oldestReceivedIndex] = false;
                Log.info("get: Skipping packets " + getIndex + "-" + oldestReceivedIndex);
                getIndex = oldestReceivedIndex = (oldestReceivedIndex + 1) % bufferLength;
                b = true;
            }
            while (!bufferFlag[oldestReceivedIndex] && oldestReceivedIndex != putIndex) {
                oldestReceivedIndex = (oldestReceivedIndex + 1) % bufferLength;
            }

//            int d = getIndex > putIndex ? (putIndex + bufferLength - getIndex) : putIndex - getIndex;
//            Log.info("d=" + d);
        }

        return b;
    }

    public void reset() {
        putIndex = 0;
        getIndex = 0;
        remoteFrameNum = 0;
        frameOffset = 0;
    }

    public void setFrameDelay(int frameDelay) {
        this.frameDelay = frameDelay;
    }

    public int getFrameDelay() {
        return frameDelay;
    }

//    public void setSimulationDelay(float simulationDelay) {
//        this.simulationDelay = simulationDelay;
//    }

//    public float getSimulationDelay() {
//        return simulationDelay;
//    }

    public void debug(String tag) {
        Log.info(tag + ": putIndex=" + putIndex + " getIndex=" + getIndex + " oldestReceivedIndex" + oldestReceivedIndex);
    }
}
