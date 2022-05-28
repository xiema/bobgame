package com.xam.bobgame.entity;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.esotericsoftware.minlog.Log;
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
                0, 0.5f, 0.5f, 0.1f, 0.8f, false);
        TextureDef textureDef = new TextureDef();
        textureDef.type = TextureDef.TextureType.PlayerBall;
        textureDef.wh = 32;
        textureDef.textureVal1 = 16;
        textureDef.color.set(color);
        GraphicsComponent graphics = ComponentFactory.graphics(engine, textureDef, 1, 1, 0);
        BuffableComponent buffable = ComponentFactory.buffable(engine);

        entity.add(identity);
        entity.add(physicsBody);
        entity.add(graphics);
        entity.add(buffable);

        return entity;
    }

    public static Entity createStar(Engine engine) {
        Entity entity = engine.createEntity();

        IdentityComponent identity = createIdentity(engine, EntityType.Pickup);
        PhysicsBodyComponent physicsBody = ComponentFactory.physicsBody(engine, BodyDef.BodyType.DynamicBody, GameProperties.START_X, GameProperties.START_Y, GameProperties.LINEAR_DAMPENING,
                0, 0.2f, 2f, 0.1f, 0.2f, false);
        physicsBody.fixtureDef.filter.categoryBits = 2;
        physicsBody.fixtureDef.filter.maskBits = (short) (0xFFFF - 2);
        physicsBody.bodyDef.angularVelocity = MathUtils.random(GameProperties.PICKUP_MIN_ANGULAR_VEL, GameProperties.PICKUP_MAX_ANGULAR_VEL);
        physicsBody.bodyDef.angularDamping = 0.1f;
        physicsBody.bodyDef.fixedRotation = false;

        TextureDef textureDef = new TextureDef();
        textureDef.type = TextureDef.TextureType.Star;
        textureDef.wh = 32;
        textureDef.textureVal1 = 16;
        textureDef.textureVal2 = 4;
        textureDef.color.set(Color.YELLOW);
        GraphicsComponent graphics = ComponentFactory.graphics(engine, textureDef, 0.4f, 0.4f, 0);

        PickupComponent pickup = ComponentFactory.pickup(engine, 10f);

        entity.add(identity);
        entity.add(physicsBody);
        entity.add(graphics);
        entity.add(pickup);

        return entity;
    }

    public static Entity createHoleHazard(Engine engine, float x, float y, float radius) {
        Entity entity = engine.createEntity();

        IdentityComponent identity = createIdentity(engine, EntityType.Hazard);
        PhysicsBodyComponent physicsBody = ComponentFactory.physicsBody(engine, BodyDef.BodyType.KinematicBody, x, y, 0,
                0, 0.25f, 0, 0, 0, true);
        physicsBody.fixtureDef.filter.categoryBits = 2;
        physicsBody.fixtureDef.filter.maskBits = ~2;

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
