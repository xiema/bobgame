package com.xam.bobgame.graphics.animators;


import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.net.NetSerializable;

public abstract class Animator<T extends Animated> implements NetSerializable, Poolable {

    protected T object;
    protected float accumulator;
    protected boolean finished = false;

    public Animator() {
    }

    public void update(float delta) {
        accumulator += delta;
        animate();
    }

    public void restart() {
        accumulator = 0;
    }

    public void setObject(T object) {
        this.object = object;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    public boolean isFinished() {
        return finished;
    }

    protected abstract void animate();

    @Override
    public void reset() {
        this.object = null;
        accumulator = 0;
        finished = false;
    }
}
