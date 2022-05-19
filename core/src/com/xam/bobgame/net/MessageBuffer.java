package com.xam.bobgame.net;

import com.esotericsoftware.minlog.Log;

public class MessageBuffer {

    private final Message[] messages;

    private int getIndex = 0;
    private int putIndex = 0;

    private int remoteFrameNum = 0;
    int syncFrameNum = 0;

    private int frameDelay = 0;

    public MessageBuffer(int length) {
        messages = new Message[length];
        for (int i = 0; i < messages.length; ++i) messages[i] = new Message(NetDriver.DATA_MAX_SIZE);
    }

    public void setFrameDelay(int frameDelay) {
        this.frameDelay = frameDelay;
    }

    public void receive(Message message, int packetFrameNum) {
        synchronized (messages) {
            message.copyTo(messages[putIndex]);
            putIndex = (putIndex + 1) % messages.length;
        }
        remoteFrameNum = Math.max(remoteFrameNum, packetFrameNum);
        syncFrameNum = Math.max(syncFrameNum, remoteFrameNum - frameDelay);
    }

    public boolean get(Message out) {
        synchronized (messages) {
            if (messages[getIndex].messageId == -1) return false;
            if (messages[getIndex].getType() == Message.MessageType.Update && messages[getIndex].frameNum > syncFrameNum) {
                return false;
            }
            messages[getIndex].copyTo(out);
            messages[getIndex].clear();
            getIndex = (getIndex + 1) % messages.length;
        }
        return true;
    }
}
