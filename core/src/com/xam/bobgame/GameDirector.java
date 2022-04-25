package com.xam.bobgame;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.xam.bobgame.entity.EntityFactory;
import com.xam.bobgame.game.ControlSystem;

public class GameDirector extends EntitySystem {

    private Array<Entity> sortedEntities = new Array<>(true, 4);
    private ImmutableArray<Entity> entities = new ImmutableArray<>(sortedEntities);

    public GameDirector(int priority) {
        super(priority);
    }

    @Override
    public void addedToEngine(Engine engine) {
        engine.addEntityListener(new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
                sortedEntities.add(entity);
            }

            @Override
            public void entityRemoved(Entity entity) {
                sortedEntities.removeValue(entity, true);
            }
        });
    }

    public ImmutableArray<Entity> getEntities () {
        return entities;
    }

    private Entity playerEntity;

    public Entity getPlayerEntity() {
        return playerEntity;
    }

    public void setupGame() {
        Engine engine = getEngine();

        Entity entity = EntityFactory.createPlayer(engine);
        engine.addEntity(entity);

        ControlSystem controlSystem = engine.getSystem(ControlSystem.class);
        controlSystem.registerEntity(entity, 0);

        playerEntity = entity;
    }
}
