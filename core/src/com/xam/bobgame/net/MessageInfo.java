package com.xam.bobgame.net;

public class MessageInfo {
    public int messageId;
    public Message.MessageType type;

    public void set(Message message) {
        messageId = message.messageId;
        type = message.getType();
    }
}
