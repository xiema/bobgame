package com.xam.bobgame.events;

import com.badlogic.ashley.core.Entity;
import com.xam.bobgame.buffs.Buff;

public class BuffEndedEvent implements GameEvent {

    public Entity entity;
    public int entityId = -1;
    public Buff buff;

    @Override
    public void reset() {
        entity = null;
        entityId = -1;
        buff = null;
    }

    @Override
    public String toString() {
        return "BuffEnded " + entityId + " " + buff;
    }
}
