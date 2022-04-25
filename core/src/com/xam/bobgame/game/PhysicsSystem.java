package com.xam.bobgame.game;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.PolygonShape;
import com.badlogic.gdx.physics.box2d.World;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;

public class PhysicsSystem extends EntitySystem {
    private ImmutableArray<Entity> entities;

    private World world = new World(new Vector2(0, 0), true);
    private Body[] walls = new Body[4];
    private boolean enabled = false;

    public PhysicsSystem(int priority) {
        super(priority);
    }

    private void createWalls() {
        BodyDef bodyDef = new BodyDef();
        PolygonShape shape = new PolygonShape();

        bodyDef.position.set(5, 0);
        shape.setAsBox(10, 0.5f);
        walls[0] = world.createBody(bodyDef);
        walls[0].createFixture(shape, 0);

        bodyDef.position.set(0, 5);
        shape.setAsBox(0.5f, 10);
        walls[1] = world.createBody(bodyDef);
        walls[1].createFixture(shape, 0);

        bodyDef.position.set(5, 10);
        shape.setAsBox(10, 0.5f);
        walls[2] = world.createBody(bodyDef);
        walls[2].createFixture(shape, 0);

        bodyDef.position.set(10, 5);
        shape.setAsBox(0.5f, 10);
        walls[3] = world.createBody(bodyDef);
        walls[3].createFixture(shape, 0);

        shape.dispose();
    }

    @Override
    public void addedToEngine(Engine engine) {
        createWalls();

        entities = engine.getEntitiesFor(Family.all(PhysicsBodyComponent.class).get());
        engine.addEntityListener(Family.all(PhysicsBodyComponent.class).get(), new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
                PhysicsBodyComponent physicsBody = ComponentMappers.physicsBody.get(entity);
                physicsBody.body = world.createBody(physicsBody.bodyDef);
                physicsBody.fixture = physicsBody.body.createFixture(physicsBody.fixtureDef);
            }

            @Override
            public void entityRemoved(Entity entity) {

            }
        });
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void removedFromEngine(Engine engine) {
        entities = null;
    }

    @Override
    public void update(float deltaTime) {
        if (enabled) world.step(1/60f, 6, 2);
//        for (Entity entity : entities) {
//            PositionComponent position = ComponentMappers.position.get(entity);
//            VelocityComponent velocity = ComponentMappers.velocity.get(entity);
//            PhysicsBodyComponent physicsBody = ComponentMappers.physicsBody.get(entity);
//
//            position.vec.mulAdd(velocity.vec, deltaTime);
//            position.vec.x = MathUtils.clamp(position.vec.x, 0, 500);
//            position.vec.y = MathUtils.clamp(position.vec.y, 0, 500);
//        }
    }
}
