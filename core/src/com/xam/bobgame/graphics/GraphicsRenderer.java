package com.xam.bobgame.graphics;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.physics.box2d.Transform;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.xam.bobgame.BoBGame;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.components.AIComponent;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.components.IdentityComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.game.PhysicsSystem;

public class GraphicsRenderer {
    private GameEngine engine;
    private ImmutableArray<Entity> entities;
    private ImmutableArray<Entity> aiEntities;
    private Stage stage;

    @SuppressWarnings("unchecked")
    private final Array<Entity>[] zSortedEntities = new Array[4];

    private ShapeRenderer shapeRenderer;

    private ObjectMap<Family, EntityListener> entityListeners = new ObjectMap<>();

    public GraphicsRenderer(GameEngine engine, final Stage stage) {
        this.engine = engine;
        this.stage = stage;

        for (int i = 0; i < 4; ++i) zSortedEntities[i] = new Array<>();

        entities = engine.getEntitiesFor(Family.all(PhysicsBodyComponent.class, GraphicsComponent.class).get());
        aiEntities = engine.getEntitiesFor(Family.all(AIComponent.class).get());

        entityListeners.put(Family.all(GraphicsComponent.class).get(), new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
                GraphicsComponent graphics = ComponentMappers.graphics.get(entity);
                if (!BoBGame.isHeadless()) graphics.spriteActor.getSprite().setRegion(new TextureRegion(graphics.textureDef.createTexture()));
                zSortedEntities[graphics.z].add(entity);
            }

            @Override
            public void entityRemoved(Entity entity) {
                GraphicsComponent graphics = ComponentMappers.graphics.get(entity);

                zSortedEntities[graphics.z].removeValue(entity, true);
            }
        });

        for (ObjectMap.Entry<Family, EntityListener> entry : entityListeners) {
            engine.addEntityListener(entry.key, entry.value);
        }

        shapeRenderer = new ShapeRenderer();
        shapeRenderer.setAutoShapeType(true);
    }

    private Vector2 tempVec = new Vector2();

    public void draw(Batch batch) {
        Camera camera = stage.getViewport().getCamera();
        camera.update();

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin();
        drawBackground();
        shapeRenderer.end();

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

//            Log.info("entity " + EntityUtils.getId(entity) + " posXError=" + physicsHistory.posXError.getAverage() + " posYError=" + physicsHistory.posYError.getAverage());
        }

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (int i = 3; i >= 1; --i) {
            drawEntities(batch, i);
        }
        drawWalls(batch);
        batch.end();
        shapeRenderer.begin();
        drawGuideLine();
        drawTargets();
        shapeRenderer.end();
        batch.begin();
        drawEntities(batch, 0);
        batch.end();
    }

    private void drawEntities(Batch batch, int z) {
        Array<Entity> zEntities = zSortedEntities[z];
        for (Entity entity : zEntities) {
            IdentityComponent iden = ComponentMappers.identity.get(entity);
            if (iden.despawning) continue;
            GraphicsComponent graphics = ComponentMappers.graphics.get(entity);
            graphics.spriteActor.draw(batch, 1);
        }
    }

    private void drawBackground() {
        shapeRenderer.setColor(Color.DARK_GRAY);
        shapeRenderer.set(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.rect(0, 0, GameProperties.MAP_WIDTH, GameProperties.MAP_HEIGHT);
    }

    private void drawGuideLine() {
        Entity entity = engine.getSystem(RefereeSystem.class).getLocalPlayerEntity();
        if (entity == null || ComponentMappers.identity.get(entity).despawning) return;

        PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
        Transform tfm = pb.body.getTransform();
        tempVec.set(tfm.getOrientation()).scl(400).add(tfm.getPosition());
        shapeRenderer.setProjectionMatrix(stage.getCamera().combined);
        shapeRenderer.setColor(Color.GREEN);
        shapeRenderer.set(ShapeRenderer.ShapeType.Line);
        shapeRenderer.line(tfm.getPosition(), tempVec);
    }

    private void drawWalls(Batch batch) {
        Sprite[] wallSprites = engine.getSystem(PhysicsSystem.class).getWallSprites();
        for (Sprite sprite : wallSprites) {
            sprite.draw(batch);
        }
    }

    private void drawTargets() {
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.set(ShapeRenderer.ShapeType.Filled);
        for (Entity entity : aiEntities) {
            AIComponent ai = ComponentMappers.ai.get(entity);
            shapeRenderer.circle(ai.target.getX(), ai.target.getY(), 0.1f, 4);
        }
    }
}
