package com.xam.bobgame.components;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.buffs.Buff;
import com.xam.bobgame.utils.BitPacker;

public class BuffableComponent implements Component2, Poolable {

    public Array<Buff> buffs = new Array<>();

    @Override
    public void reset() {
        buffs.clear();
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        return 0;
    }
}
