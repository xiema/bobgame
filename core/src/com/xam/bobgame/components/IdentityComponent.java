package com.xam.bobgame.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;

public class IdentityComponent implements Component, Poolable {
    public int id = -1;

    @Override
    public void reset() {
        id = -1;
    }
}
