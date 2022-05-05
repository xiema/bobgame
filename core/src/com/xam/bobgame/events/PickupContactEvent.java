package com.xam.bobgame.events;

import com.badlogic.ashley.core.Entity;

public class PickupContactEvent implements GameEvent {
    public Entity entity = null;
    public Entity pickup = null;

    @Override
    public void reset() {
        entity = null;
        pickup = null;
    }
}
