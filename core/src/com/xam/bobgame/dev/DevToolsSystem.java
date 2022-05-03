package com.xam.bobgame.dev;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Null;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.components.IdentityComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.game.PhysicsSystem;

public class DevToolsSystem extends EntitySystem {
    public DevTools devTools;

    public Viewport engineViewport;

    public ImmutableArray<Entity> allEntities;
    public ImmutableArray<Entity> focusableEntities;
    public ShapeRenderer shapeRenderer;

    public @Null Entity focusedEntity;

    public ObjectMap<Family, EntityListener> entityListeners = new ObjectMap<>();

    public DevToolsSystem(DevTools devTools, Stage devUIStage) {
        super(200);
        this.devTools = devTools;
        shapeRenderer = new ShapeRenderer();
        shapeRenderer.setColor(Color.GREEN);
        shapeRenderer.setAutoShapeType(true);

        devUIStage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button == 1) { // right click
                    focusedEntity = getEntityUnderCursor();
                }
                return false;
            }
        });
        engineViewport = devTools.game.getWorldViewport();

        entityListeners.put(Family.all().get(), new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {

            }

            @Override
            public void entityRemoved(Entity entity) {
                if (entity == focusedEntity) {
                    focusedEntity = null;
                }
            }
        });
    }

    @Override
    public void addedToEngine(Engine engine) {
        allEntities = engine.getEntitiesFor(Family.all(IdentityComponent.class).get());
        focusableEntities = engine.getEntitiesFor(Family.all(PhysicsBodyComponent.class, GraphicsComponent.class).get());

        for (ObjectMap.Entry<Family, EntityListener> entry : entityListeners) engine.addEntityListener(entry.key, entry.value);

        devTools.onAddedToEngine(engine);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        allEntities = null;
        focusableEntities = null;
        focusedEntity = null;
        for (EntityListener listener : entityListeners.values()) engine.removeEntityListener(listener);
    }

    @Override
    public void update(float deltaTime) {
    }

    public void draw(Batch batch) {
        if (engineViewport != null) {
//            engineViewport.apply(true);
//            if (debugCollision) {
//                shapeRenderer.setProjectionMatrix(engineViewport.getCamera().combined);
//                shapeRenderer.begin();
//                for (Entity entity : colliderEntities) {
//                    PositionComponent position = ComponentMappers.position.get(entity);
//                    ColliderComponent collider = ComponentMappers.collider.get(entity);
//
//                    shapeRenderer.setColor(collider.collisions == 0 ? Color.GREEN : Color.RED);
//
//                    if (collider.colliderType == CollisionDetection.ColliderType.OBB) {
//                        CollisionDetection.drawOBB(shapeRenderer, position, collider);
//                    }
//                    else {
//                        CollisionDetection.drawCircle(shapeRenderer, position, collider);
//                    }
//
//                    shapeRenderer.setColor(graphicDebugColor);
//                    GraphicsComponent graphics = ComponentMappers.graphics.get(entity);
//                    if (graphics != null && graphics.graphic != null) drawGraphicBounds(shapeRenderer, graphics.graphic);
//                }
//
//                shapeRenderer.end();
//            }
        }
    }

    private Vector2 tempVec = new Vector2();
    public Entity getEntityUnderCursor() {
        if (engineViewport != null) {
            tempVec.set(Gdx.input.getX(), Gdx.input.getY());
            engineViewport.unproject(tempVec);
            return getEntityAt(tempVec.x, tempVec.y);
        }
        return null;
    }

    private float focusRadius = 0.1f;

    public Entity getEntityAt(float x, float y) {
        return getEngine().getSystem(PhysicsSystem.class).queryAABB(x - focusRadius, y - focusRadius, x + focusRadius, y + focusRadius);
    }
}
