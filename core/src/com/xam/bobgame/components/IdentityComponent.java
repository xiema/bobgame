package com.xam.bobgame.components;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.entity.EntityType;
import com.xam.bobgame.utils.BitPacker;

public class IdentityComponent extends Component2 implements Poolable {
    public int id = -1;
    public EntityType type = EntityType.Neutral;
    public boolean despawning = false;

    @Override
    public void reset() {
        id = -1;
        type = EntityType.Neutral;
        despawning = false;
    }

    @Override
    public void read(BitPacker packer, Engine engine, boolean write) {
        // id should be manually set
        type = EntityType.values()[readInt(packer, type.getValue(), 0, EntityType.values().length, write)];
    }
}
