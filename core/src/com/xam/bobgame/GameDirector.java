package com.xam.bobgame;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.entity.EntityFactory;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.events.*;
import com.xam.bobgame.game.ControlSystem;
import com.xam.bobgame.net.NetDriver;

public class GameDirector extends EntitySystem {

    private Array<Entity> sortedEntities = new Array<>(true, 4);
    private ImmutableArray<Entity> entities = new ImmutableArray<>(sortedEntities);

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    private Color[] playerColor = {
            Color.WHITE, Color.BLUE,
    };

    private int playerCount = 0;

    public GameDirector(int priority) {
        super(priority);

        listeners.put(ClientConnectedEvent.class, new EventListenerAdapter<ClientConnectedEvent>() {
            @Override
            public void handleEvent(ClientConnectedEvent event) {
                joinPlayer(event.connectionId);
                getEngine().getSystem(NetDriver.class).flagSnapshot(event.connectionId);
            }
        });
        listeners.put(PlayerAssignEvent.class, new EventListenerAdapter<PlayerAssignEvent>() {
            @Override
            public void handleEvent(PlayerAssignEvent event) {
                for (Entity entity : entities) {
                    if (EntityUtils.getId(entity) == event.entityId) {
                        setPlayerEntity(entity);
                    }
                }
            }
        });
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
        engine.getSystem(EventsSystem.class).addListeners(listeners);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        engine.getSystem(EventsSystem.class).removeListeners(listeners);
    }

    public ImmutableArray<Entity> getEntities () {
        return entities;
    }

    private Entity playerEntity;
    private int playerEntityId = -1;

    public Entity getPlayerEntity() {
        return playerEntity;
    }

    public int getPlayerEntityId() {
        return playerEntityId;
    }

    public void setupGame() {
        int playerId = playerCount;
        playerCount++;

        Engine engine = getEngine();

        Entity entity = EntityFactory.createPlayer(engine, playerColor[playerId % playerColor.length]);
        engine.addEntity(entity);

        ControlSystem controlSystem = engine.getSystem(ControlSystem.class);
        controlSystem.registerEntity(entity, playerId);

        setPlayerEntity(entity);
    }

    public void setPlayerEntity(Entity entity) {
        this.playerEntity = entity;
        playerEntityId = EntityUtils.getId(entity);
    }

    public int joinPlayer(int connectionId) {
        int playerId = playerCount;
        playerCount++;

        Engine engine = getEngine();
        Entity entity = EntityFactory.createPlayer(engine, playerColor[playerId % playerColor.length]);
        engine.addEntity(entity);

        ControlSystem controlSystem = engine.getSystem(ControlSystem.class);
        controlSystem.registerEntity(entity, playerId);

        engine.getSystem(NetDriver.class).addPlayerConnection(playerId, connectionId);

        PlayerAssignEvent event = Pools.obtain(PlayerAssignEvent.class);
        event.entityId = EntityUtils.getId(entity);
        engine.getSystem(NetDriver.class).queueClientEvent(connectionId, event);

        return playerId;
    }
}
