package com.xam.bobgame.graphics;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.game.PhysicsSystem;
import com.xam.bobgame.utils.MathUtils2;

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
            Body body = physicsBody.body;
            PhysicsSystem.PhysicsHistory physicsHistory = (PhysicsSystem.PhysicsHistory) body.getUserData();
            Vector2 pos = body.getPosition();
            float x = pos.x + physicsHistory.posXError.getAverage() - (physicsBody.xJitterCount > 1 ? physicsBody.displacement.x / 2 : 0);
            float y = pos.y + physicsHistory.posYError.getAverage() - (physicsBody.yJitterCount > 1 ? physicsBody.displacement.y / 2 : 0);
            graphics.spriteActor.getSprite().setOriginBasedPosition(x, y);
        }
        stage.draw();
    }
}
