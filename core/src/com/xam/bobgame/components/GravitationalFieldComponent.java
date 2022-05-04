package com.xam.bobgame.components;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.Fixture;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class GravitationalFieldComponent extends Component2 implements Poolable {

    public Fixture fixture;
    public float strength = 0;
    public float radius = 0;
    public Array<Entity> affectedEntities = new Array<>();

    @Override
    public void reset() {
        fixture = null;
        strength = 0;
        radius = 0;
        affectedEntities.clear();
    }

    @Override
    public void read(BitPacker packer, Engine engine, boolean write) {
        strength = readFloat(packer, strength, 0, NetDriver.MAX_GRAVITY_STRENGTH, NetDriver.RES_GRAVITY_STRENGTH, write);
        radius = readFloat(packer, radius, 0, NetDriver.MAX_GRAVITY_RADIUS, NetDriver.RES_GRAVITY_RADIUS, write);
    }
}
