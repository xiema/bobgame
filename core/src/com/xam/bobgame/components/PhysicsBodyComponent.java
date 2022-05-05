package com.xam.bobgame.components;

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
import com.xam.bobgame.utils.BitPacker;

public class PhysicsBodyComponent extends Component2 implements Poolable {
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
    public void read(BitPacker packer, Engine engine, boolean write) {
        bodyDef.type = BodyDef.BodyType.values()[readInt(packer, bodyDef.type.getValue(), 0, BodyDef.BodyType.values().length, write)];
        bodyDef.position.x = readFloat(packer, bodyDef.position.x, -3, GameProperties.MAP_WIDTH + 3, NetDriver.RES_POSITION, write);
        bodyDef.position.y = readFloat(packer, bodyDef.position.y, -3, GameProperties.MAP_HEIGHT + 3, NetDriver.RES_POSITION, write);
        bodyDef.linearDamping = readFloat(packer, bodyDef.linearDamping, 0, 1, NetDriver.RES_MASS, write);
        shapeDef.type = ShapeDef.ShapeType.values()[readInt(packer, shapeDef.type.getValue(), 0, ShapeDef.ShapeType.values().length, write)];
        shapeDef.shapeVal1 = readFloat(packer, shapeDef.shapeVal1, 0, 16, NetDriver.RES_POSITION, write);
        fixtureDef.density = readFloat(packer, fixtureDef.density, 0, 16, NetDriver.RES_MASS, write);
        fixtureDef.friction = readFloat(packer, fixtureDef.friction, 0, 16, NetDriver.RES_MASS, write);
        fixtureDef.restitution = readFloat(packer, fixtureDef.restitution, 0, 16, NetDriver.RES_MASS, write);
        fixtureDef.isSensor = readBoolean(packer, fixtureDef.isSensor, write);
        fixtureDef.filter.categoryBits = (short) readInt(packer, fixtureDef.filter.categoryBits & 0xFFFF, 0, 0xFFFF, write);
        fixtureDef.filter.maskBits = (short) readInt(packer, fixtureDef.filter.maskBits & 0xFFFF, 0, 0xFFFF, write);
    }
}
