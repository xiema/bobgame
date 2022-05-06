package com.xam.bobgame.buffs;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;

public abstract class BuffDef {

    String name;
    float duration;
    float secondsPerTick;
    float timingOutThreshold;
    boolean stackable;

    BuffDef(String name, float duration, float secondsPerTick, float timingOutThreshold, boolean stackable) {
        this.name = name;
        this.duration = duration;
        this.secondsPerTick = secondsPerTick;
        this.timingOutThreshold = timingOutThreshold;
        this.stackable = stackable;
    }

    public abstract void apply(Engine engine, Buff buff, Entity entity);

    public abstract void tick(Engine engine, Buff buff, Entity entity);

    public abstract void unapply(Engine engine, Buff buff, Entity entity);
}
