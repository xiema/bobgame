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
import com.badlogic.gdx.utils.Pools;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.xam.bobgame.BoBGame;
import com.xam.bobgame.buffs.Buff;
import com.xam.bobgame.components.*;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.game.PhysicsSystem;
import com.xam.bobgame.graphics.animators.Animator;
import com.xam.bobgame.graphics.animators.BlinkAnimator;

public class GraphicsRenderer {
    private GameEngine engine;
    private ImmutableArray<Entity> entities;
    private ImmutableArray<Entity> aiEntities;

    private Viewport viewport;

    @SuppressWarnings("unchecked")
    private final Array<Entity>[] zSortedEntities = new Array[4];

    private ShapeRenderer shapeRenderer;

    private ObjectMap<Family, EntityListener> entityListeners = new ObjectMap<>();

    public static final Class<?>[] animatorClasses = {
            BlinkAnimator.class,
    };

    public static int getAnimatorClassIndex(Class<? extends Animator> clazz) {
        for (int i = 0; i < animatorClasses.length; ++i) {
            if (animatorClasses[i] == clazz) return i;
        }
        return -1;
    }

    public GraphicsRenderer(GameEngine engine, Viewport viewport) {
        this.engine = engine;
        this.viewport = viewport;

        for (int i = 0; i < 4; ++i) zSortedEntities[i] = new Array<>();

        entities = engine.getEntitiesFor(Family.all(PhysicsBodyComponent.class, GraphicsComponent.class).get());
        aiEntities = engine.getEntitiesFor(Family.all(AIComponent.class).get());

        entityListeners.put(Family.all(GraphicsComponent.class).get(), new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
                GraphicsComponent graphics = ComponentMappers.graphics.get(entity);
                if (!BoBGame.isHeadless()) graphics.sprite.setRegion(new TextureRegion(graphics.textureDef.createTexture()));
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

    private final Vector2 tempVec = new Vector2();
    private final Vector2 tempVec2 = new Vector2();

    public void update(float deltaTime) {
        updateEntities(deltaTime);
    }

    public void draw(Batch batch) {
        Camera camera = viewport.getCamera();
        camera.update();
        shapeRenderer.setProjectionMatrix(camera.combined);
        batch.setProjectionMatrix(camera.combined);

        shapeRenderer.begin();
        drawBackground();
        shapeRenderer.end();

        batch.begin();
        for (int i = 3; i >= 1; --i) {
            drawEntities(batch, i);
        }
        drawWalls(batch);
        batch.end();

        shapeRenderer.begin();
        drawGuideLine();
        shapeRenderer.end();

        batch.begin();
        drawEntities(batch, 0);
        batch.end();
    }

    private void updateEntities(float deltaTime) {
        for (Entity entity: entities) {
            PhysicsBodyComponent physicsBody = ComponentMappers.physicsBody.get(entity);
            GraphicsComponent graphics = ComponentMappers.graphics.get(entity);

            // reset for animators
            graphics.drawTint.set(graphics.baseTint);
            graphics.drawOffsets.setZero();
            graphics.drawOrientation = 0;

            BuffableComponent buffable = ComponentMappers.buffables.get(entity);
            if (buffable != null) {
                for (Buff buff : buffable.buffs) {
                    for (int i = 0; i < buff.animators.size; ) {
                        Animator<GraphicsComponent> animator = buff.animators.get(i);
                        animator.setObject(graphics);
                        animator.update(deltaTime);
                        if (animator.isFinished()) {
                            buff.animators.removeIndex(i);
                            Pools.free(animator);
                        }
                        else {
                            i++;
                        }
                    }
                }
            }

            Body body = physicsBody.body;
            PhysicsSystem.PhysicsHistory physicsHistory = (PhysicsSystem.PhysicsHistory) body.getUserData();
            Transform tfm = body.getTransform();
            Vector2 pos = tfm.getPosition();
            float x = pos.x + graphics.drawOffsets.x + physicsHistory.posXError.getAverage() - (physicsBody.xJitterCount > 1 ? physicsBody.displacement.x / 2 : 0);
            float y = pos.y + graphics.drawOffsets.y + physicsHistory.posYError.getAverage() - (physicsBody.yJitterCount > 1 ? physicsBody.displacement.y / 2 : 0);
            graphics.sprite.setColor(graphics.drawTint);
            graphics.sprite.setOriginBasedPosition(x, y);
            graphics.sprite.setRotation(MathUtils.radiansToDegrees * tfm.getRotation() + graphics.drawOrientation + 90);

//            Log.info("entity " + EntityUtils.getId(entity) + " posXError=" + physicsHistory.posXError.getAverage() + " posYError=" + physicsHistory.posYError.getAverage());
        }
    }

    private void drawEntities(Batch batch, int z) {
        Array<Entity> zEntities = zSortedEntities[z];
        for (Entity entity : zEntities) {
            IdentityComponent iden = ComponentMappers.identity.get(entity);
            if (iden.despawning) continue;
            GraphicsComponent graphics = ComponentMappers.graphics.get(entity);
            graphics.sprite.draw(batch, 1);
        }
    }

    private void drawBackground() {
        shapeRenderer.setColor(Color.DARK_GRAY);
        shapeRenderer.set(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.rect(0, 0, GameProperties.MAP_WIDTH, GameProperties.MAP_HEIGHT);
    }

    private void drawGuideLine() {
        Entity entity = engine.getSystem(RefereeSystem.class).getLocalPlayerEntity();
        if (entity == null || !EntityUtils.isAdded(entity)) return;

        PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
        Transform tfm = pb.body.getTransform();
        PhysicsSystem.PhysicsHistory physicsHistory = (PhysicsSystem.PhysicsHistory) pb.body.getUserData();

        tempVec.set(tfm.getOrientation()).scl(400).add(tfm.getPosition());
        tempVec2.set(tfm.getPosition()).add(physicsHistory.posXError.getAverage(), physicsHistory.posYError.getAverage());

        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.setColor(Color.GREEN);
        shapeRenderer.set(ShapeRenderer.ShapeType.Line);
        shapeRenderer.line(tempVec2, tempVec);
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
