package com.xam.bobgame.events;

import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.net.Message;

public class PlayerAssignEvent extends NetDriver.NetworkEvent {

    public int playerId = -1;
    public int entityId = -1;

    @Override
    public void reset() {
        super.reset();
        entityId = -1;
        playerId = -1;
    }

    @Override
    public void read(Message.MessageBuilder builder, boolean write) {
        playerId = readInt(builder, playerId, -1, 31, write);
        entityId = readInt(builder, entityId, 0, 255, write);
    }

    @Override
    public String toString() {
        return "PlayerAssignEvent playerId=" + playerId + " entityId=" + entityId;
    }
}
