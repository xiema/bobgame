package com.xam.bobgame.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.badlogic.gdx.utils.Pools;
import com.xam.bobgame.buffs.Buff;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.net.NetSerializable;
import com.xam.bobgame.utils.BitPacker;

public class BuffableComponent implements Component, NetSerializable, Poolable {

    public Array<Buff> buffs = new Array<>();

    @Override
    public void reset() {
        buffs.clear();
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        if (packer.isWriteMode()) {
            packer.packInt(buffs.size, 0, NetDriver.MAX_BUFF_COUNT);
            for (Buff buff : buffs) {
                buff.read(packer, engine);
            }
        }
        else {
            int i, count = packer.unpackInt(0, NetDriver.MAX_BUFF_COUNT);
            for (i = 0; i < count; ++i) {
                Buff buff;
                if (i >= buffs.size) {
                    buff = Pools.obtain(Buff.class);
                    buffs.add(buff);
                }
                else {
                    buff = buffs.get(i);
                }
                buff.read(packer, engine);
            }
            while (i < buffs.size) {
                Pools.free(buffs.get(i));
                buffs.removeIndex(i);
            }
        }
        return 0;
    }
}
