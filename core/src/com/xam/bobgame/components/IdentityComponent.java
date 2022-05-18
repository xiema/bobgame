package com.xam.bobgame.components;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.entity.EntityType;
import com.xam.bobgame.utils.BitPacker;

public class IdentityComponent implements Component2, Poolable {
    public int id = -1;
    public EntityType type = EntityType.Neutral;
    public boolean spawning = true;
    public boolean despawning = false;

    @Override
    public void reset() {
        id = -1;
        type = EntityType.Neutral;
        spawning = true;
        despawning = false;
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        // id should be manually set
        type = EntityType.values()[packer.readInt(type.getValue(), 0, EntityType.values().length)];
        return 0;
    }
}
