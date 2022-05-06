package com.xam.bobgame.events;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.GameProperties;
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
    public int read(BitPacker packer, Engine engine) {
        controlId = packer.readInt(controlId, -1, 31);
        entityId = packer.readInt(entityId, 0, NetDriver.MAX_ENTITY_ID);
        x = packer.readFloat(x, -3, GameProperties.MAP_WIDTH + 3, NetDriver.RES_POSITION);
        y = packer.readFloat(y, -3,  GameProperties.MAP_HEIGHT + 3, NetDriver.RES_POSITION);
        buttonId = packer.readInt(buttonId, 0, 1);
        buttonState = packer.readInt(buttonState ? 1 : 0, 0, 1) > 0;
        return 0;
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
