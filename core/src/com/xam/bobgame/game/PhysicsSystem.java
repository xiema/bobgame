package com.xam.bobgame.game;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.xam.bobgame.components.PositionComponent;
import com.xam.bobgame.components.VelocityComponent;
import com.xam.bobgame.entity.ComponentMappers;

public class PhysicsSystem extends EntitySystem {
    private ImmutableArray<Entity> entities;

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(Family.all(PositionComponent.class, VelocityComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        entities = null;
    }

    @Override
    public void update(float deltaTime) {
        for (Entity entity : entities) {
            PositionComponent position = ComponentMappers.position.get(entity);
            VelocityComponent velocity = ComponentMappers.velocity.get(entity);
            position.vec.mulAdd(velocity.vec, deltaTime);
        }
    }
}
