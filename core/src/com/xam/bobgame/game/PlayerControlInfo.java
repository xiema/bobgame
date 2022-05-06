package com.xam.bobgame.game;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.math.Vector2;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.net.NetSerializable;
import com.xam.bobgame.utils.BitPacker;

public class PlayerControlInfo implements NetSerializable {

    public Vector2 cursorPosition = new Vector2(Vector2.Zero);
    public boolean buttonState = false;
    public float holdDuration = -1;

    public void reset() {
        cursorPosition.setZero();
        holdDuration = 0;
        buttonState = false;
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        buttonState = packer.readBoolean(buttonState);
        holdDuration = packer.readFloat(holdDuration, -NetDriver.RES_HOLD_DURATION, GameProperties.CHARGE_DURATION_2, NetDriver.RES_HOLD_DURATION);
        return 0;
    }
}
