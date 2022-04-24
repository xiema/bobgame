package com.xam.bobgame.graphics;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.components.PositionComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.utils.DebugUtils;

public class GraphicsRenderer {
    private ImmutableArray<Entity> entities;
    private Stage stage;

    public GraphicsRenderer(Engine engine, final Stage stage) {
        this.stage = stage;
        entities = engine.getEntitiesFor(Family.all(PositionComponent.class, GraphicsComponent.class).get());
        engine.addEntityListener(Family.all(GraphicsComponent.class).get(), new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
                stage.addActor(new SpriteActor(entity));
            }

            @Override
            public void entityRemoved(Entity entity) {

            }
        });
    }

    public void draw(Batch batch) {
        stage.draw();
//        for (Entity entity: entities) {
//            PositionComponent position = ComponentMappers.position.get(entity);
//            GraphicsComponent graphics = ComponentMappers.graphics.get(entity);
//            batch.draw(graphics.txtReg, position.vec.x, position.vec.y);
//        }
    }
}
