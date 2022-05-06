package com.xam.bobgame.game;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.buffs.BuffDefs;
import com.xam.bobgame.buffs.BuffSystem;
import com.xam.bobgame.components.IdentityComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityFactory;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.events.*;
import com.xam.bobgame.net.ConnectionManager;
import com.xam.bobgame.net.NetDriver;

public class RefereeSystem extends EntitySystem {

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();
    private ObjectMap<Family, EntityListener> entityListeners = new ObjectMap<>();

    private Color[] playerColor = {
            Color.CYAN, Color.BLUE, Color.YELLOW, Color.LIME, Color.GOLD, Color.SCARLET, Color.VIOLET,
    };

    private int playerCount = 0;
    private int localPlayerId = -1;

    private final PlayerInfo[] playerInfos = new PlayerInfo[NetDriver.MAX_CLIENTS];

//    private final boolean[] playerExists = new boolean[NetDriver.MAX_CLIENTS];
//    private final int[] playerControlMap = new int[NetDriver.MAX_CLIENTS];
//    private final int[] playerScores = new int[NetDriver.MAX_CLIENTS];
//    private final float[] playerRespawnTimes = new float[NetDriver.MAX_CLIENTS];

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
                    removePlayer(event.playerId, !event.cleanDisconnect);
                }
                else {
                    Log.debug("Client " + event.clientId + " disconnected without joining");
                }
            }
        });
        listeners.put(PlayerDeathEvent.class, new EventListenerAdapter<PlayerDeathEvent>() {
            @Override
            public void handleEvent(PlayerDeathEvent event) {
                if (playerInfos[event.playerId].controlledEntityId == event.entityId) playerInfos[event.playerId].controlledEntityId = -1;
            }
        });
        listeners.put(PlayerBallSpawnedEvent.class, new EventListenerAdapter<PlayerBallSpawnedEvent>() {
            @Override
            public void handleEvent(PlayerBallSpawnedEvent event) {
                playerInfos[event.playerId].controlledEntityId = event.entityId;
            }
        });
        listeners.put(PlayerScoreEvent.class, new EventListenerAdapter<PlayerScoreEvent>() {
            @Override
            public void handleEvent(PlayerScoreEvent event) {
                // TODO: combine enabled flag for systems
                if (enabled) {
                    playerInfos[event.playerId].score += event.scoreIncrement;
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

        for (int i = 0; i < playerInfos.length; ++i) playerInfos[i] = new PlayerInfo();
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
        for (PlayerInfo playerInfo : playerInfos) {
            playerInfo.reset();
        }
        playerCount = 0;
        matchStarted = false;
        localPlayerId = -1;
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < playerInfos.length; ++i) {
            if (!playerInfos[i].inPlay) continue;
            playerInfos[i].respawnTime = Math.max(0, playerInfos[i].respawnTime - deltaTime);
            if (enabled && playerInfos[i].respawnTime <= 0 && playerInfos[i].controlledEntityId == -1) spawnPlayerBall(i);
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
        return playerInfos[localPlayerId].controlledEntityId;
    }

    public int getLocalPlayerId() {
        return localPlayerId;
    }

    public int getPlayerEntityId(int playerId) {
        return playerInfos[playerId].controlledEntityId;
    }

    public Entity getPlayerEntity(int playerId) {
        return ((GameEngine) getEngine()).getEntityById(getPlayerEntityId(playerId));
    }

    public int getEntityPlayerId(int entityId) {
        for (int i = 0; i < playerInfos.length; ++i) {
            if (playerInfos[i].controlledEntityId == entityId) return i;
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
        Log.debug("PlayerId set to " + playerId);
    }

    public void joinGame() {
        if (((GameEngine) getEngine()).getMode() == NetDriver.Mode.Client) {
            RequestJoinEvent requestJoinEvent = Pools.obtain(RequestJoinEvent.class);
            getEngine().getSystem(NetDriver.class).queueClientEvent(-1, requestJoinEvent, false);
        }
    }

    private int getEmptyPlayerSlot() {
        for (int i = 0; i < playerInfos.length; ++i) {
            if (!playerInfos[i].inPlay) return i;
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

        playerInfos[playerId].inPlay = true;
        playerCount++;

//        spawnPlayerBall(playerId);

        if (clientId == -1) {
            localPlayerId = playerId;
        }
        else {
            // remote client
            ConnectionManager connectionManager = netDriver.getConnectionManager();
            connectionManager.getConnectionSlot(clientId).setPlayerId(playerId);
            assignPlayer(clientId, playerId);
        }

        PlayerJoinedEvent joinedEvent = Pools.obtain(PlayerJoinedEvent.class);
        joinedEvent.playerId = playerId;
        netDriver.queueClientEvent(-1, joinedEvent);
        engine.getSystem(EventsSystem.class).triggerEvent(joinedEvent);
    }

    public void assignPlayer(int clientId, int playerId) {
        Log.info("Client " + clientId + " joined as Player " + playerId);
        PlayerAssignEvent assignEvent = Pools.obtain(PlayerAssignEvent.class);
        assignEvent.playerId = playerId;
        getEngine().getSystem(NetDriver.class).queueClientEvent(clientId, assignEvent, false);
    }

    public void removePlayer(int playerId, boolean kicked) {
        GameEngine engine = (GameEngine) getEngine();
        Entity entity = engine.getEntityById(playerInfos[playerId].controlledEntityId);
        if (entity != null) engine.removeEntity(entity);
        playerInfos[playerId].reset();

        if (kicked) {
            Log.info("Player " + playerId + " was kicked from the game.");
        }
        else {
            Log.info("Player " + playerId + " left the game.");
        }

        PlayerLeftEvent event = Pools.obtain(PlayerLeftEvent.class);
        event.playerId = playerId;
        event.kicked = kicked;
        engine.getSystem(NetDriver.class).queueClientEvent(-1, event);
        engine.getSystem(EventsSystem.class).queueEvent(event);

        playerCount--;
    }

    private Entity spawnPlayerBall(int playerId) {
        Engine engine = getEngine();
        Entity entity = EntityFactory.createPlayer(engine, playerColor[playerId % playerColor.length]);
        PhysicsBodyComponent physicsBody = ComponentMappers.physicsBody.get(entity);
        physicsBody.bodyDef.position.x = MathUtils.random(GameProperties.PLAYER_SPAWN_MARGIN, GameProperties.MAP_WIDTH - GameProperties.PLAYER_SPAWN_MARGIN);
        physicsBody.bodyDef.position.y = MathUtils.random(GameProperties.PLAYER_SPAWN_MARGIN, GameProperties.MAP_HEIGHT - GameProperties.PLAYER_SPAWN_MARGIN);

        engine.addEntity(entity);

        BuffSystem.addBuff(entity, entity, BuffDefs.SpawnInvBuffDef, 3);

        int entityId = EntityUtils.getId(entity);
        playerInfos[playerId].controlledEntityId = entityId;

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

        playerInfos[playerId].score--;

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

        playerInfos[playerId].respawnTime = GameProperties.PLAYER_RESPAWN_TIME;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public PlayerInfo getPlayerInfo(int playerId) {
        return playerInfos[playerId];
    }
}
