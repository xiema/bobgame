package com.xam.bobgame.net;

import com.esotericsoftware.minlog.Log;

/**
 * A buffer for Messages. Keeps Messages for a number of frames before they can be retrieved.
 */
public class MessageBuffer {

    private final Message[] messages;

    private int getIndex = 0;
    private int putIndex = 0;

    /**
     * The latest frame number of packets received from the remote host. Used to compute syncFrameNum.
     */
    private int remoteFrameNum = 0;
    /**
     * The minimum frame number to be kept in the buffer. Messages with frame numbers below this will be retrieved.
     */
    int syncFrameNum = 0;

    /**
     * Number of frames to delay before messages can be retrieved.
     */
    private int frameDelay = 0;

    public MessageBuffer(int length) {
        messages = new Message[length];
        for (int i = 0; i < messages.length; ++i) messages[i] = new Message(NetDriver.DATA_MAX_SIZE);
    }

    public void setFrameDelay(int frameDelay) {
        this.frameDelay = frameDelay;
    }

    public void incrementSyncFrameNum() {
        syncFrameNum = Math.min(remoteFrameNum, syncFrameNum + 1);
    }

    /**
     * Adds a message to the buffer and updates the frame numbers.
     * @param message Message to put in the buffer
     * @param packetFrameNum Frame number of the packet that contained the message
     */
    public void receive(Message message, int packetFrameNum) {
        synchronized (messages) {
            message.copyTo(messages[putIndex]);
            putIndex = (putIndex + 1) % messages.length;
        }
        remoteFrameNum = Math.max(remoteFrameNum, packetFrameNum);
        syncFrameNum = Math.max(syncFrameNum, remoteFrameNum - frameDelay);
    }

    /**
     * Attempts to retrieve a message from the buffer, returning true if successful.
     */
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
