package com.xam.bobgame.events;

import com.badlogic.ashley.core.Entity;

public class HazardContactEvent implements GameEvent {
    public Entity entity = null;
    public Entity hazard = null;

    @Override
    public void reset() {
        entity = null;
        hazard = null;
    }
}
