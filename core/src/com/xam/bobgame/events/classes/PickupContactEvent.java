package com.xam.bobgame.events.classes;

import com.badlogic.ashley.core.Entity;
import com.xam.bobgame.events.GameEvent;

public class PickupContactEvent implements GameEvent {
    public Entity entity = null;
    public Entity pickup = null;

    @Override
    public void reset() {
        entity = null;
        pickup = null;
    }
}
