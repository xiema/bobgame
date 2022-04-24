package com.xam.bobgame.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pool.Poolable;

public class VelocityComponent implements Component, Poolable {
    public Vector2 vec = new Vector2();

    @Override
    public void reset() {

    }
}
