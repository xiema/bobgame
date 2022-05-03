package com.xam.bobgame;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntityListener;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityFactory;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.events.*;
import com.xam.bobgame.game.ControlSystem;
import com.xam.bobgame.net.NetDriver;

import java.util.Arrays;

public class GameDirector extends EntitySystem {

    private Array<Entity> sortedEntities = new Array<>(true, 4);
    private ImmutableArray<Entity> entities = new ImmutableArray<>(sortedEntities);

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    private Color[] playerColor = {
            Color.CYAN, Color.BLUE, Color.YELLOW, Color.LIME, Color.GOLD, Color.SCARLET, Color.VIOLET,
    };

    private int playerCount = 0;
    private final IntMap<Entity> entityMap = new IntMap<>();

    private final int[] playerControlMap = new int[NetDriver.MAX_CLIENTS];
    private final int[] playerScores = new int[NetDriver.MAX_CLIENTS];

    public GameDirector(int priority) {
        super(priority);

        listeners.put(ClientConnectedEvent.class, new EventListenerAdapter<ClientConnectedEvent>() {
            @Override
            public void handleEvent(ClientConnectedEvent event) {
                int playerId = joinPlayer(event.clientId);
                getEngine().getSystem(NetDriver.class).getServer().flagSnapshot(event.clientId);
            }
        });
    }

    @Override
    public void addedToEngine(Engine engine) {
        engine.addEntityListener(new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
                sortedEntities.add(entity);
                entityMap.put(EntityUtils.getId(entity), entity);
            }

            @Override
            public void entityRemoved(Entity entity) {
                sortedEntities.removeValue(entity, true);
                entityMap.remove(EntityUtils.getId(entity));
            }
        });
        engine.getSystem(EventsSystem.class).addListeners(listeners);
        Arrays.fill(playerControlMap, -1);
        Arrays.fill(playerScores, 0);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
        if (eventsSystem != null) eventsSystem.removeListeners(listeners);
        entityMap.clear();
        sortedEntities.clear();
    }

    public ImmutableArray<Entity> getEntities () {
        return entities;
    }

    private int localPlayerId = -1;
//    private Entity playerEntity;
//    private int playerEntityId = -1;

    public Entity getEntityById(int entityId) {
        return entityMap.get(entityId, null);
    }

    public Entity getLocalPlayerEntity() {
        int entityId = getLocalPlayerEntityId();
        return entityId == -1 ? null : entityMap.get(entityId);
    }

    public int getLocalPlayerEntityId() {
        if (localPlayerId == -1) return -1;
        IntArray entityIds = getEngine().getSystem(ControlSystem.class).getControlledEntityIds(localPlayerId);
        if (entityIds.size == 0) return -1;
        return entityIds.get(0);
    }

    public int getLocalPlayerId() {
        return localPlayerId;
    }

    public int getPlayerEntityId(int playerId) {
        return playerControlMap[playerId];
    }

    public Entity getPlayerEntity(int playerId) {
        return getEntityById(getPlayerEntityId(playerId));
    }

    public int getEntityPlayerId(int entityId) {
        for (int i = 0; i < playerControlMap.length; ++i) {
            if (playerControlMap[i] == entityId) return i;
        }
        return -1;
    }

    public void setupGame() {
        localPlayerId = joinPlayer(-1);

        Entity entity = EntityFactory.createHoleHazard(getEngine(), MathUtils.random(2, GameProperties.MAP_WIDTH -2), MathUtils.random(2, GameProperties.MAP_HEIGHT -2), 2);
        getEngine().addEntity(entity);
    }

    public void setLocalPlayerId(int playerId) {
        localPlayerId = playerId;
        Log.info("PlayerId set to " + playerId);
    }

    public int joinPlayer(int clientId) {
        int playerId = playerCount;
        playerCount++;

        Engine engine = getEngine();
        Entity entity = EntityFactory.createPlayer(engine, playerColor[playerId % playerColor.length]);
        engine.addEntity(entity);

        ControlSystem controlSystem = engine.getSystem(ControlSystem.class);
        int entityId = EntityUtils.getId(entity);
        controlSystem.registerEntity(entityId, playerId);
        playerControlMap[playerId] = entityId;

        // remote client
        if (clientId != -1) engine.getSystem(NetDriver.class).getServer().acceptConnection(clientId, playerId);

        PlayerJoinedEvent joinedEvent = Pools.obtain(PlayerJoinedEvent.class);
        joinedEvent.playerId = playerId;
        getEngine().getSystem(EventsSystem.class).triggerEvent(joinedEvent);

        return playerId;
    }

    public void killPlayer(int playerId) {
        Entity entity = getPlayerEntity(playerId);
        PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);

        pb.body.setTransform(pb.bodyDef.position.x, pb.bodyDef.position.y, 0);
        pb.body.setLinearVelocity(0, 0);

        playerScores[playerId]--;

        PlayerDeathEvent event = Pools.obtain(PlayerDeathEvent.class);
        event.playerId = playerId;
        event.playerScore = playerScores[playerId];
        getEngine().getSystem(EventsSystem.class).queueEvent(event);
    }
}
