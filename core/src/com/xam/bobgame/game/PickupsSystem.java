package com.xam.bobgame.game;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.components.IdentityComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.components.PickupComponent;
import com.xam.bobgame.entity.*;
import com.xam.bobgame.events.*;

public class PickupsSystem extends EntitySystem {

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();
    private ObjectMap<Family, EntityListener> entityListeners = new ObjectMap<>();

    private ImmutableArray<Entity> pickupEntities;

    public PickupsSystem(int priority) {
        super(priority);

        listeners.put(PickupContactEvent.class, new EventListenerAdapter<PickupContactEvent>() {
            private final Vector2 tempVec = new Vector2();
            @Override
            public void handleEvent(PickupContactEvent event) {
                if (((GameEngine) getEngine()).getMode() == GameEngine.Mode.Server) {
                    IdentityComponent pickupIden = ComponentMappers.identity.get(event.pickup);
                    if (pickupIden.despawning) return;

                    IdentityComponent iden = ComponentMappers.identity.get(event.entity);

                    if (iden.type == EntityType.Player) {
                        getEngine().removeEntity(event.pickup);

                        int playerId = getEngine().getSystem(RefereeSystem.class).getEntityPlayerId(iden.id);
                        if (playerId == -1) {
                            Log.warn("Player entity does not belong to any player.");
                            return;
                        }
                        PlayerScoreEvent scoreEvent = Pools.obtain(PlayerScoreEvent.class);
                        scoreEvent.playerId = playerId;
                        scoreEvent.scoreIncrement = 1;
//                        NetDriver netDriver = getEngine().getSystem(NetDriver.class);
//                        netDriver.queueClientEvent(-1, scoreEvent);
                        getEngine().getSystem(EventsSystem.class).triggerEvent(scoreEvent);
                    }
                    else if (iden.type == EntityType.Hazard) {
                        Body body = ComponentMappers.physicsBody.get(event.entity).body;
                        tempVec.set(body.getLinearVelocity()).nor().scl(GameProperties.PICKUP_PUSH_STRENGTH);
                        body.applyForceToCenter(tempVec, true);
                    }
                }
            }
        });
    }

    @Override
    public void addedToEngine(Engine engine) {
        engine.getSystem(EventsSystem.class).addListeners(listeners);
        for (ObjectMap.Entry<Family, EntityListener> entry : entityListeners) {
            engine.addEntityListener(entry.key, entry.value);
        }
        pickupEntities = engine.getEntitiesFor(Family.all(PickupComponent.class).get());
    }

    @Override
    public void removedFromEngine(Engine engine) {
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
        if (eventsSystem != null) eventsSystem.removeListeners(listeners);
        for (ObjectMap.Entry<Family, EntityListener> entry : entityListeners) {
            engine.removeEntityListener(entry.value);
        }
        pickupEntities = null;
    }

    private float timer = 0;
    private float nextPickupSpawn = 0;

    @Override
    public void update(float deltaTime) {
        if (((GameEngine) getEngine()).getMode() == GameEngine.Mode.Server) {
            timer += deltaTime;
            if (timer > nextPickupSpawn) {
                timer -= nextPickupSpawn;
                spawnStar();
                nextPickupSpawn = MathUtils.random(1, GameProperties.PICKUP_SPAWN_COOLDOWN);
            }

            for (Entity entity : pickupEntities) {
                PickupComponent pickup = ComponentMappers.pickups.get(entity);
                pickup.timeAlive += deltaTime;
                if (pickup.timeAlive > pickup.maxLifeTime) {
                    getEngine().removeEntity(entity);
                }
            }
        }
    }

    private void spawnStar() {
        float x = MathUtils.random(1, GameProperties.MAP_WIDTH - 1);
        float y = MathUtils.random(1, GameProperties.MAP_HEIGHT - 1);

        Entity entity = EntityFactory.createStar(getEngine());
        PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
        pb.bodyDef.position.x = x;
        pb.bodyDef.position.y = y;

        getEngine().addEntity(entity);
    }
}
