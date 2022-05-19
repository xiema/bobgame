package com.xam.bobgame.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.net.NetSerializable;
import com.xam.bobgame.utils.BitPacker;

public class HazardComponent implements Component, NetSerializable, Poolable {
    @Override
    public void reset() {

    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        return 0;
    }
}
