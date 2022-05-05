package com.xam.bobgame.components;

import com.badlogic.gdx.utils.Pool.Poolable;

public class PickupComponent extends Component2 implements Poolable {

    public float timeAlive = 0;
    public float maxLifeTime = Float.MAX_VALUE;

    @Override
    public void reset() {
        timeAlive = 0;
        maxLifeTime = Float.MAX_VALUE;
    }
}
