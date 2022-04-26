package com.xam.bobgame.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.math.Circle;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.utils.Pool;
import com.xam.bobgame.game.ShapeDef;

public class PhysicsBodyComponent implements Component, Pool.Poolable {
    public BodyDef bodyDef;
    public FixtureDef fixtureDef;
    public ShapeDef shapeDef;

    public Body body;
    public Fixture fixture;

    @Override
    public void reset() {

    }
}
