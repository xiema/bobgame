package com.xam.bobgame.components;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.utils.BitPacker;

public class PickupComponent implements Component2, Poolable {

    public float timeAlive = 0;
    public float maxLifeTime = Float.MAX_VALUE;

    @Override
    public void reset() {
        timeAlive = 0;
        maxLifeTime = Float.MAX_VALUE;
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        return 0;
    }
}
