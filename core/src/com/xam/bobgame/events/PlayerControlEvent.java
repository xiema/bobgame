package com.xam.bobgame.events;

import com.xam.bobgame.utils.BitPacker;
import com.xam.bobgame.net.NetDriver;

public class PlayerControlEvent extends NetDriver.NetworkEvent {
    public int controlId = -1;
    public int entityId = -1;
    public float x, y;
    public int buttonId;
    public boolean buttonState;

    @Override
    public void reset() {
        super.reset();
        controlId = -1;
        entityId = -1;
    }

    @Override
    public void read(BitPacker builder, boolean write) {
        controlId = readInt(builder, controlId, -1, 31, write);
        entityId = readInt(builder, entityId, 0, 255, write);
        x = readFloat(builder, x, -3,  13, NetDriver.RES_POSITION, write);
        y = readFloat(builder, y, -3,  13, NetDriver.RES_POSITION, write);
        buttonId = readInt(builder, buttonId, 0, 1, write);
        buttonState = readInt(builder, buttonState ? 1 : 0, 0, 1, write) > 0;
    }

    @Override
    public NetDriver.NetworkEvent copyTo(NetDriver.NetworkEvent event) {
        PlayerControlEvent other = (PlayerControlEvent) event;
        other.controlId = controlId;
        other.entityId = entityId;
        other.x = x;
        other.y = y;
        other.buttonId = buttonId;
        other.buttonState = buttonState;
        return super.copyTo(event);
    }

    @Override
    public String toString() {
        return "PlayerControlEvent controlId=" + controlId + " entityId=" + entityId;
    }
}
