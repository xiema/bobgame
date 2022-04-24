package com.xam.bobgame.events;

import com.badlogic.gdx.math.Vector2;

public class PlayerControlEvent implements GameEvent {
    public int controlId = -1;
    public int entityId = -1;
    public float x, y;

    @Override
    public void reset() {
        controlId = -1;
        entityId = -1;
    }
}
