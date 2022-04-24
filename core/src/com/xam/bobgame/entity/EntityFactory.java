package com.xam.bobgame.entity;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.components.IdentityComponent;
import com.xam.bobgame.components.PositionComponent;
import com.xam.bobgame.components.VelocityComponent;

public class EntityFactory {

    private static int nextId = 0;

    private static IdentityComponent createIdentity(Engine engine) {
        IdentityComponent identity = engine.createComponent(IdentityComponent.class);
        identity.id = nextId++;
        return identity;
    }

    public static Entity createPlayer(Engine engine) {
        Entity entity = engine.createEntity();

        IdentityComponent identity = createIdentity(engine);
        PositionComponent position = engine.createComponent(PositionComponent.class);
        VelocityComponent velocity = engine.createComponent(VelocityComponent.class);
        GraphicsComponent graphics = engine.createComponent(GraphicsComponent.class);

        position.vec.set(128, 128);
        velocity.vec.setZero();

        Pixmap pmap = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
        pmap.setColor(Color.WHITE);
        pmap.fillCircle(16, 16, 16);
        Texture tx = new Texture(pmap);
        pmap.dispose();

        graphics.txtReg = new TextureRegion(tx);

        entity.add(identity);
        entity.add(position);
        entity.add(velocity);
        entity.add(graphics);

        return entity;
    }
}
