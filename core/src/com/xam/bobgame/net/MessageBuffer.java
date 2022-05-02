package com.xam.bobgame.net;

public class MessageBuffer {

    private final Message[] messages;

    private int getIndex = 0;
    private int putIndex = 0;

    public MessageBuffer(int length) {
        messages = new Message[length];
        for (int i = 0; i < messages.length; ++i) messages[i] = new Message(NetDriver.DATA_MAX_SIZE);
    }

    public void receive(Message message) {
        synchronized (messages) {
            message.copyTo(messages[putIndex]);
            putIndex = (putIndex + 1) % messages.length;
        }
    }

    public boolean get(Message out) {
        synchronized (messages) {
            if (messages[getIndex].messageId == -1) return false;
            messages[getIndex].copyTo(out);
            messages[getIndex].clear();
            getIndex = (getIndex + 1) % messages.length;
        }
        return true;
    }
}
