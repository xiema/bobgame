package com.xam.bobgame.entity;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.components.*;
import com.xam.bobgame.graphics.TextureDef;

public class EntityFactory {

    private static int nextId = 0;

    private static IdentityComponent createIdentity(Engine engine, EntityType type) {
        return ComponentFactory.identity(engine, nextId++, type);
    }

    public static Entity createPlayer(Engine engine, Color color) {
        Entity entity = engine.createEntity();

        IdentityComponent identity = createIdentity(engine, EntityType.Player);
        PhysicsBodyComponent physicsBody = ComponentFactory.physicsBody(engine, BodyDef.BodyType.DynamicBody, GameProperties.START_X, GameProperties.START_Y, GameProperties.LINEAR_DAMPENING,
                0, 0.5f, 0.5f, 0.1f, 0.8f);
        TextureDef textureDef = new TextureDef();
        textureDef.type = TextureDef.TextureType.PlayerBall;
        textureDef.wh = 32;
        textureDef.textureVal1 = 16;
        textureDef.color.set(color);
        GraphicsComponent graphics = ComponentFactory.graphics(engine, textureDef, 1, 1, 0);

        entity.add(identity);
        entity.add(physicsBody);
        entity.add(graphics);

        return entity;
    }

    public static Entity createHoleHazard(Engine engine, float x, float y, float radius) {
        Entity entity = engine.createEntity();

        IdentityComponent identity = createIdentity(engine, EntityType.Hazard);
        PhysicsBodyComponent physicsBody = ComponentFactory.physicsBody(engine, BodyDef.BodyType.KinematicBody, x, y, 0,
                0, 0.25f, 0, 0, 0);
        TextureDef textureDef = new TextureDef();
        textureDef.type = TextureDef.TextureType.HazardHole;
        textureDef.wh = 128;
        textureDef.textureVal1 = 64;
        textureDef.color.set(Color.BLACK);
        GraphicsComponent graphics = ComponentFactory.graphics(engine, textureDef, radius * 2, radius * 2, 3);
        HazardComponent hazard = ComponentFactory.hazard(engine);
        GravitationalFieldComponent gravField = ComponentFactory.gravField(engine, 40, 30);

        SteerableComponent steerable = engine.createComponent(SteerableComponent.class);
        steerable.maxLinearSpeed = 5f;
        steerable.maxLinearAcceleration = 20f;
        AIComponent ai = engine.createComponent(AIComponent.class);

        entity.add(identity);
        entity.add(physicsBody);
        entity.add(graphics);
        entity.add(hazard);
        entity.add(gravField);
        entity.add(steerable);
        entity.add(ai);

        return entity;
    }
}
