package com.xam.bobgame.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.game.ShapeDef;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.net.NetSerializable;
import com.xam.bobgame.utils.BitPacker;

public class PhysicsBodyComponent implements Component, NetSerializable, Poolable {
    public BodyDef bodyDef;
    public FixtureDef fixtureDef;
    public ShapeDef shapeDef;

    public Body body;
    public Fixture fixture;

    public final Vector2 displacement = new Vector2();
    public final Vector2 prevPos = new Vector2();

    public int xJitterCount = 0;
    public int yJitterCount = 0;

    @Override
    public void reset() {
        xJitterCount = 0;
        yJitterCount = 0;
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        bodyDef.type = BodyDef.BodyType.values()[packer.readInt(bodyDef.type.getValue(), 0, BodyDef.BodyType.values().length)];
        bodyDef.position.x = packer.readFloat(bodyDef.position.x, -3, GameProperties.MAP_WIDTH + 3, NetDriver.RES_POSITION);
        bodyDef.position.y = packer.readFloat(bodyDef.position.y, -3, GameProperties.MAP_HEIGHT + 3, NetDriver.RES_POSITION);
        bodyDef.linearDamping = packer.readFloat(bodyDef.linearDamping, 0, 1, NetDriver.RES_MASS);
        shapeDef.type = ShapeDef.ShapeType.values()[packer.readInt(shapeDef.type.getValue(), 0, ShapeDef.ShapeType.values().length)];
        shapeDef.shapeVal1 = packer.readFloat(shapeDef.shapeVal1, 0, 16, NetDriver.RES_POSITION);
        fixtureDef.density = packer.readFloat(fixtureDef.density, 0, 16, NetDriver.RES_MASS);
        fixtureDef.friction = packer.readFloat(fixtureDef.friction, 0, 16, NetDriver.RES_MASS);
        fixtureDef.restitution = packer.readFloat(fixtureDef.restitution, 0, 16, NetDriver.RES_MASS);
        fixtureDef.isSensor = packer.readBoolean(fixtureDef.isSensor);
        fixtureDef.filter.categoryBits = (short) packer.readInt(fixtureDef.filter.categoryBits & 0xFFFF, 0, 0xFFFF);
        fixtureDef.filter.maskBits = (short) packer.readInt(fixtureDef.filter.maskBits & 0xFFFF, 0, 0xFFFF);
        return 0;
    }
}
