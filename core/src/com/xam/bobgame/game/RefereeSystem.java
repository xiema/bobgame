package com.xam.bobgame.game;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.components.IdentityComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityFactory;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.events.*;
import com.xam.bobgame.net.ConnectionManager;
import com.xam.bobgame.net.NetDriver;

import java.util.Arrays;

public class RefereeSystem extends EntitySystem {

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();
    private ObjectMap<Family, EntityListener> entityListeners = new ObjectMap<>();

    private Color[] playerColor = {
            Color.CYAN, Color.BLUE, Color.YELLOW, Color.LIME, Color.GOLD, Color.SCARLET, Color.VIOLET,
    };

    private int playerCount = 0;
    private int localPlayerId = -1;

    private final boolean[] playerExists = new boolean[NetDriver.MAX_CLIENTS];
    private final int[] playerControlMap = new int[NetDriver.MAX_CLIENTS];
    private final int[] playerScores = new int[NetDriver.MAX_CLIENTS];
    private final float[] playerRespawnTime = new float[NetDriver.MAX_CLIENTS];

    private boolean enabled = false;

    private boolean matchStarted = false;

    public RefereeSystem(int priority) {
        super(priority);

        listeners.put(ClientConnectedEvent.class, new EventListenerAdapter<ClientConnectedEvent>() {
            @Override
            public void handleEvent(ClientConnectedEvent event) {
                NetDriver netDriver = getEngine().getSystem(NetDriver.class);
                if (netDriver.getMode() == NetDriver.Mode.Server) {
                    netDriver.getServer().flagSnapshot(event.clientId);
                }
                else {
                    ((GameEngine) getEngine()).resumeGame();
                }
            }
        });
        listeners.put(ClientDisconnectedEvent.class, new EventListenerAdapter<ClientDisconnectedEvent>() {
            @Override
            public void handleEvent(ClientDisconnectedEvent event) {
                if (event.playerId != -1) {
                    leavePlayer(event.playerId);
                }
                else {
                    Log.debug("Client " + event.clientId + " disconnected without joining");
                }
            }
        });
        listeners.put(PlayerDeathEvent.class, new EventListenerAdapter<PlayerDeathEvent>() {
            @Override
            public void handleEvent(PlayerDeathEvent event) {
                if (playerControlMap[event.playerId] == event.entityId) playerControlMap[event.playerId] = -1;
            }
        });
        listeners.put(PlayerBallSpawnedEvent.class, new EventListenerAdapter<PlayerBallSpawnedEvent>() {
            @Override
            public void handleEvent(PlayerBallSpawnedEvent event) {
                playerControlMap[event.playerId] = event.entityId;
            }
        });
        listeners.put(PlayerScoreEvent.class, new EventListenerAdapter<PlayerScoreEvent>() {
            @Override
            public void handleEvent(PlayerScoreEvent event) {
                // TODO: combine enabled flag for systems
                if (enabled) {
                    playerScores[event.playerId] += event.scoreIncrement;
                    ScoreBoardRefreshEvent scoreBoardEvent = Pools.obtain(ScoreBoardRefreshEvent.class);
                    getEngine().getSystem(NetDriver.class).queueClientEvent(-1, scoreBoardEvent, true);
                    getEngine().getSystem(EventsSystem.class).triggerEvent(scoreBoardEvent);
                }
            }
        });
        listeners.put(RequestJoinEvent.class, new EventListenerAdapter<RequestJoinEvent>() {
            @Override
            public void handleEvent(RequestJoinEvent event) {
                joinPlayer(event.clientId);
            }
        });
        listeners.put(PlayerAssignEvent.class, new EventListenerAdapter<PlayerAssignEvent>() {
            @Override
            public void handleEvent(PlayerAssignEvent event) {
                setLocalPlayerId(event.playerId);
            }
        });
    }

    @Override
    public void addedToEngine(Engine engine) {
        EntityUtils.addEntityListeners(engine, entityListeners);
        engine.getSystem(EventsSystem.class).addListeners(listeners);
        reset();
    }

    @Override
    public void removedFromEngine(Engine engine) {
        EntityUtils.removeEntityListeners(engine, entityListeners);
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
        if (eventsSystem != null) eventsSystem.removeListeners(listeners);
        reset();
    }

    private void reset() {
        Arrays.fill(playerExists, false);
        Arrays.fill(playerControlMap, -1);
        Arrays.fill(playerScores, 0);
        Arrays.fill(playerRespawnTime, 0);
        playerCount = 0;
        matchStarted = false;
        localPlayerId = -1;
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < playerRespawnTime.length; ++i) {
            if (!playerExists[i]) continue;
            playerRespawnTime[i] -= deltaTime;
            if (enabled && playerRespawnTime[i] <= 0 && playerControlMap[i] == -1) spawnPlayerBall(i);
        }
    }


    public boolean isMatchStarted() {
        return matchStarted;
    }

    public boolean isLocalPlayerJoined() {
        return localPlayerId != -1;
    }

    public Entity getLocalPlayerEntity() {
        int entityId = getLocalPlayerEntityId();
        return entityId == -1 ? null : ((GameEngine) getEngine()).getEntityById(entityId);
    }

    public int getLocalPlayerEntityId() {
        if (localPlayerId == -1) return -1;
        return playerControlMap[localPlayerId];
    }

    public int getLocalPlayerId() {
        return localPlayerId;
    }

    public int getPlayerEntityId(int playerId) {
        return playerControlMap[playerId];
    }

    public Entity getPlayerEntity(int playerId) {
        return ((GameEngine) getEngine()).getEntityById(getPlayerEntityId(playerId));
    }

    public int getEntityPlayerId(int entityId) {
        for (int i = 0; i < playerControlMap.length; ++i) {
            if (playerControlMap[i] == entityId) return i;
        }
        return -1;
    }

    public void setupGame() {
        Entity entity = EntityFactory.createHoleHazard(getEngine(), MathUtils.random(2, GameProperties.MAP_WIDTH -2), MathUtils.random(2, GameProperties.MAP_HEIGHT -2), 2);
        getEngine().addEntity(entity);
        matchStarted = true;
    }

    public void setLocalPlayerId(int playerId) {
        localPlayerId = playerId;
        Log.info("PlayerId set to " + playerId);
    }

    public void joinGame() {
        if (((GameEngine) getEngine()).getMode() == NetDriver.Mode.Client) {
            RequestJoinEvent requestJoinEvent = Pools.obtain(RequestJoinEvent.class);
            getEngine().getSystem(NetDriver.class).queueClientEvent(-1, requestJoinEvent, false);
        }
    }

    private int getEmptyPlayerSlot() {
        for (int i = 0; i < playerExists.length; ++i) {
            if (!playerExists[i]) return i;
        }
        return -1;
    }

    public void joinPlayer(int clientId) {
        GameEngine engine = (GameEngine) getEngine();
        NetDriver netDriver = engine.getSystem(NetDriver.class);

        int playerId = getEmptyPlayerSlot();
        if (playerId == -1) {
            Log.info("Too many players");
            return;
        }

        playerExists[playerId] = true;
        playerCount++;

        spawnPlayerBall(playerId);

        if (clientId == -1) {
            localPlayerId = playerId;
        }
        else {
            // remote client
            ConnectionManager connectionManager = netDriver.getConnectionManager();
            connectionManager.getConnectionSlot(clientId).setPlayerId(playerId);
            connectionManager.acceptConnection(clientId);
            Log.info("Client " + clientId + " joined as Player " + playerId);
            PlayerAssignEvent assignEvent = Pools.obtain(PlayerAssignEvent.class);
            assignEvent.playerId = playerId;
            netDriver.queueClientEvent(clientId, assignEvent, false);
        }

        PlayerJoinedEvent joinedEvent = Pools.obtain(PlayerJoinedEvent.class);
        joinedEvent.playerId = playerId;
        netDriver.queueClientEvent(-1, joinedEvent);
        engine.getSystem(EventsSystem.class).triggerEvent(joinedEvent);

        return;
    }

    public void leavePlayer(int playerId) {
        GameEngine engine = (GameEngine) getEngine();
        Entity entity = engine.getEntityById(playerControlMap[playerId]);
        if (entity != null) engine.removeEntity(entity);
        playerExists[playerId] = false;
        playerControlMap[playerId] = -1;
        playerRespawnTime[playerId] = 0;
        playerScores[playerId] = 0;

        Log.info("Player " + playerId + " left the game.");

        PlayerLeftEvent event = Pools.obtain(PlayerLeftEvent.class);
        event.playerId = playerId;
        engine.getSystem(NetDriver.class).queueClientEvent(-1, event);
        engine.getSystem(EventsSystem.class).queueEvent(event);

        playerCount--;
    }

    private Entity spawnPlayerBall(int playerId) {
        Engine engine = getEngine();
        Entity entity = EntityFactory.createPlayer(engine, playerColor[playerId % playerColor.length]);
        engine.addEntity(entity);

        int entityId = EntityUtils.getId(entity);
        playerControlMap[playerId] = entityId;

        PlayerBallSpawnedEvent event = Pools.obtain(PlayerBallSpawnedEvent.class);
        event.entityId = entityId;
        event.playerId = playerId;
        getEngine().getSystem(EventsSystem.class).queueEvent(event);

        return entity;
    }

    public void killPlayer(int playerId) {
        EventsSystem eventsSystem = getEngine().getSystem(EventsSystem.class);
        Entity entity = getPlayerEntity(playerId);

        IdentityComponent iden = ComponentMappers.identity.get(entity);
        iden.despawning = true;

        PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
        pb.body.setTransform(pb.bodyDef.position.x, pb.bodyDef.position.y, 0);
        pb.body.setLinearVelocity(0, 0);

        playerScores[playerId]--;

        NetDriver netDriver = getEngine().getSystem(NetDriver.class);

        PlayerDeathEvent deathEvent = Pools.obtain(PlayerDeathEvent.class);
        deathEvent.playerId = playerId;
        deathEvent.entityId = EntityUtils.getId(entity);
        netDriver.queueClientEvent(-1, deathEvent, false);
        eventsSystem.queueEvent(deathEvent);

//        ScoreBoardUpdateEvent scoreEvent = Pools.obtain(ScoreBoardUpdateEvent.class);
//        scoreEvent.playerId = playerId;
//        netDriver.queueClientEvent(-1, scoreEvent);
//        eventsSystem.queueEvent(scoreEvent);

        ScoreBoardRefreshEvent scoreEvent = Pools.obtain(ScoreBoardRefreshEvent.class);
        netDriver.queueClientEvent(-1, scoreEvent);
        eventsSystem.queueEvent(scoreEvent);

        getEngine().removeEntity(entity);

        playerRespawnTime[playerId] = GameProperties.PLAYER_RESPAWN_TIME;
    }

    public int[] getPlayerControlMap() {
        return playerControlMap;
    }

    public int[] getPlayerScores() {
        return playerScores;
    }

    public boolean[] getPlayerExists() {
        return playerExists;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}