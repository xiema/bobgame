package com.xam.bobgame.entity;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.xam.bobgame.components.*;

public class EntityFactory {

    private static int nextId = 0;

    private static IdentityComponent createIdentity(Engine engine) {
        return ComponentFactory.identity(engine, nextId++);
    }

    public static Entity createPlayer(Engine engine) {
        Entity entity = engine.createEntity();

        IdentityComponent identity = createIdentity(engine);
        PhysicsBodyComponent physicsBody = ComponentFactory.physicsBody(engine, BodyDef.BodyType.DynamicBody,  5, 5,
                0, 0.5f, 0.5f, 0.1f, 1f);
        Texture tx = ComponentFactory.textureCircle(32, 16, Color.WHITE);
        GraphicsComponent graphics = ComponentFactory.graphics(engine, new TextureRegion(tx), 1, 1);

        entity.add(identity);
        entity.add(physicsBody);
        entity.add(graphics);

        return entity;
    }
}
