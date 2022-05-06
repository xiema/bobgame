package com.xam.bobgame.components;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.utils.BitPacker;

public class HazardComponent implements Component2, Poolable {
    @Override
    public void reset() {

    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        return 0;
    }
}
