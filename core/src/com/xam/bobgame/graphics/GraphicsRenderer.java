package com.xam.bobgame.graphics;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.utils.DebugUtils;

public class GraphicsRenderer {
    private ImmutableArray<Entity> entities;
    private Stage stage;

    public GraphicsRenderer(Engine engine, final Stage stage) {
        this.stage = stage;
        entities = engine.getEntitiesFor(Family.all(PhysicsBodyComponent.class, GraphicsComponent.class).get());
        engine.addEntityListener(Family.all(GraphicsComponent.class).get(), new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
                GraphicsComponent graphics = ComponentMappers.graphics.get(entity);
                stage.addActor(graphics.spriteActor);
            }

            @Override
            public void entityRemoved(Entity entity) {

            }
        });
    }

    public void draw(Batch batch) {
        for (Entity entity: entities) {
            PhysicsBodyComponent physicsBody = ComponentMappers.physicsBody.get(entity);
            GraphicsComponent graphics = ComponentMappers.graphics.get(entity);
            graphics.spriteActor.getSprite().setOriginBasedPosition(physicsBody.body.getPosition().x, physicsBody.body.getPosition().y);
        }
        stage.draw();
    }
}
