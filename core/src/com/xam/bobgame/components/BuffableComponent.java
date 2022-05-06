package com.xam.bobgame.components;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.buffs.Buff;

public class BuffableComponent extends Component2 implements Poolable {

    public Array<Buff> buffs = new Array<>();

    @Override
    public void reset() {
        buffs.clear();
    }
}
