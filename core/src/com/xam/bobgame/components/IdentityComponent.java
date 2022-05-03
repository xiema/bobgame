package com.xam.bobgame.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.entity.EntityType;

public class IdentityComponent implements Component, Poolable {
    public int id = -1;
    public EntityType type = EntityType.Neutral;

    @Override
    public void reset() {
        id = -1;
        type = EntityType.Neutral;
    }
}
