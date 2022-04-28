package com.xam.bobgame.net;

public class MessageInfo {
    public int seqNum;
    public Message.MessageType type;

    public void set(Message message) {
        seqNum = message.messageNum;
        type = message.getType();
    }
}
