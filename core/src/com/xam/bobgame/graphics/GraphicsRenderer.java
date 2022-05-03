package com.xam.bobgame.graphics;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Transform;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameDirector;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.game.PhysicsSystem;
import com.xam.bobgame.utils.MathUtils2;

public class GraphicsRenderer {
    private GameEngine engine;
    private ImmutableArray<Entity> entities;
    private Stage stage;

    private ShapeRenderer shapeRenderer;

    public GraphicsRenderer(GameEngine engine, final Stage stage) {
        this.engine = engine;
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
        shapeRenderer = new ShapeRenderer();
        shapeRenderer.setAutoShapeType(true);
    }

    private Vector2 tempVec = new Vector2();

    public void draw(Batch batch) {
        drawGuideLine(batch);

        for (Entity entity: entities) {
            PhysicsBodyComponent physicsBody = ComponentMappers.physicsBody.get(entity);
            GraphicsComponent graphics = ComponentMappers.graphics.get(entity);
            Body body = physicsBody.body;
            PhysicsSystem.PhysicsHistory physicsHistory = (PhysicsSystem.PhysicsHistory) body.getUserData();
            Transform tfm = body.getTransform();
            Vector2 pos = tfm.getPosition();
            float x = pos.x + physicsHistory.posXError.getAverage() - (physicsBody.xJitterCount > 1 ? physicsBody.displacement.x / 2 : 0);
            float y = pos.y + physicsHistory.posYError.getAverage() - (physicsBody.yJitterCount > 1 ? physicsBody.displacement.y / 2 : 0);
            graphics.spriteActor.getSprite().setOriginBasedPosition(x, y);
            graphics.spriteActor.getSprite().setRotation(MathUtils.radiansToDegrees * tfm.getRotation() + 90);
        }
        stage.draw();
    }

    private void drawGuideLine(Batch batch) {
        Entity entity = engine.getSystem(GameDirector.class).getLocalPlayerEntity();
        if (entity == null) return;

        boolean drawing = batch.isDrawing();
        if (drawing) batch.end();

        PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
        Transform tfm = pb.body.getTransform();
        tempVec.set(tfm.getOrientation()).scl(400).add(tfm.getPosition());
        shapeRenderer.begin();
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.setColor(Color.GREEN);
        shapeRenderer.line(tfm.getPosition(), tempVec);
        shapeRenderer.end();

        if (drawing) batch.begin();
    }
}
