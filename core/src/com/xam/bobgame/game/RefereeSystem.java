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
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityFactory;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.events.*;
import com.xam.bobgame.events.classes.*;
import com.xam.bobgame.net.ConnectionManager;
import com.xam.bobgame.net.NetDriver;

public class RefereeSystem extends EntitySystem {

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();
    private ObjectMap<Family, EntityListener> entityListeners = new ObjectMap<>();

    private Color[] playerColor = {
            Color.CYAN, Color.BLUE, Color.YELLOW, Color.LIME, Color.GOLD, Color.SCARLET, Color.VIOLET,
    };

    private int localPlayerId = -1;

    private final PlayerInfo[] playerInfos = new PlayerInfo[NetDriver.MAX_CLIENTS];

    private MatchState matchState = MatchState.NotStarted;
    private float matchTime = 0;
    private float matchDuration = GameProperties.MATCH_TIME;

    public RefereeSystem(int priority) {
        super(priority);
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
                if (matchState != MatchState.Started) return;
                if (((GameEngine) getEngine()).getMode() == GameEngine.Mode.Server) {
                    modifyPlayerScore(event.playerId, event.scoreIncrement);
                    ScoreBoardRefreshEvent scoreBoardEvent = Pools.obtain(ScoreBoardRefreshEvent.class);
                    getEngine().getSystem(NetDriver.class).queueClientEvent(-1, scoreBoardEvent, true);
                    getEngine().getSystem(EventsSystem.class).triggerEvent(scoreBoardEvent);
                }
            }
        });
        listeners.put(RequestJoinEvent.class, new EventListenerAdapter<RequestJoinEvent>() {
            @Override
            public void handleEvent(RequestJoinEvent event) {
                if (((GameEngine) getEngine()).getMode() == GameEngine.Mode.Server) {
                    joinPlayer(event.clientId);
                }
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
        for (int i = 0; i < playerInfos.length; ++i) {
            PlayerInfo playerInfo = playerInfos[i];
            playerInfo.reset();
        }
        matchState = MatchState.NotStarted;
        matchTime = 0;
        localPlayerId = -1;
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < playerInfos.length; ++i) {
            if (!playerInfos[i].inPlay) continue;
            playerInfos[i].respawnTime = Math.max(0, playerInfos[i].respawnTime - deltaTime);
            playerInfos[i].stamina = Math.max(GameProperties.PLAYER_STAMINA_MIN, Math.min(100, playerInfos[i].stamina + deltaTime * GameProperties.PLAYER_STAMINA_RECOVERY));
            if (matchState != MatchState.NotStarted && ((GameEngine) getEngine()).getMode() == GameEngine.Mode.Server && playerInfos[i].respawnTime <= 0 && playerInfos[i].controlledEntityId == -1)
                spawnPlayerBall(i);
        }

        if (matchState == MatchState.Started && ((GameEngine) getEngine()).getMode() == GameEngine.Mode.Server) {
            matchTime += deltaTime;
            if (matchTime >= matchDuration) {
                endMatch();
            }
        }
    }

    public boolean modifyPlayerScore(int playerId, int amount) {
        if (matchState == MatchState.Started && playerInfos[playerId].inPlay) {
            playerInfos[playerId].score += amount;
            return true;
        }
        return false;
    }

    public MatchState getMatchState() {
        return matchState;
    }

    public void setMatchState(MatchState matchState) {
        if (((GameEngine) getEngine()).getMode() == GameEngine.Mode.Client) {
            if (this.matchState != matchState) {
                this.matchState = matchState;
                getEngine().getSystem(EventsSystem.class).queueEvent(Pools.obtain(ConnectionStateRefreshEvent.class));
            }
        }
        else {
            Log.error("RefereeSystem", "Tried to set match state in server");
        }
    }

    public float getMatchTime() {
        return matchTime;
    }

    public void setMatchTime(float matchTime) {
        if (((GameEngine) getEngine()).getMode() == GameEngine.Mode.Client) {
            this.matchTime = matchTime;
        }
        else {
            Log.error("Attempted to set match time in Server");
        }
    }

    public boolean isEndless() {
        return matchDuration == 0;
    }

    // TODO: Use NetSerializable interface
    public float getMatchDuration() {
        return matchDuration;
    }

    public void setMatchDuration(float matchDuration) {
        if (((GameEngine) getEngine()).getMode() == GameEngine.Mode.Client) {
            this.matchDuration = matchDuration;
        }
        else {
            Log.error("Attempted to set match duration in Server");
        }
    }

    public float getMatchTimeRemaining() {
        return matchDuration - matchTime;
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
    }

    public void startMatch() {
        if (((GameEngine) getEngine()).getMode() != GameEngine.Mode.Server) {
            Log.error("Cannot start match from client");
            return;
        }
        if (matchState != MatchState.NotStarted) {
            Log.error("RefereeSystem", "Attempted to start match that is already started or has ended");
            return;
        }
        if (getPlayerCount() == 0) {
            Log.info("Cannot start match with 0 players");
            return;
        }

        matchState = MatchState.Started;
        // TODO: send specific event
        getEngine().getSystem(EventsSystem.class).queueEvent(Pools.obtain(ConnectionStateRefreshEvent.class));
    }

    public void endMatch() {
        if (((GameEngine) getEngine()).getMode() != GameEngine.Mode.Server) {
            Log.error("RefereeSystem", "Attempted to end match from client");
            return;
        }
        if (matchState != MatchState.Started) {
            Log.error("RefereeSystem", "Attempted to end a match that has not yet started or is already ended");
            return;
        }

        int winningPlayerId = -1, winningPlayerScore = 0;
        for (int i = 0; i < playerInfos.length; ++i) {
            PlayerInfo playerInfo = playerInfos[i];
            if (playerInfo.inPlay) {
                if (winningPlayerId == -1 || winningPlayerScore < playerInfo.score) {
                    winningPlayerId = i;
                    winningPlayerScore = playerInfo.score;
                }
            }
        }

        matchState = MatchState.Ended;

        if (winningPlayerId != -1) {
    //        getEngine().getSystem(EventsSystem.class).queueEvent(Pools.obtain(ConnectionStateRefreshEvent.class));
            MatchEndedEvent event = Pools.obtain(MatchEndedEvent.class);
            event.winningPlayerId = winningPlayerId;
            getEngine().getSystem(NetDriver.class).queueClientEvent(-1, event);
            getEngine().getSystem(EventsSystem.class).queueEvent(event);
        }
    }

    public void setLocalPlayerId(int playerId) {
        localPlayerId = playerId;
        Log.debug("PlayerId set to " + playerId);
    }

    public void joinGame() {
        if (((GameEngine) getEngine()).getMode() == GameEngine.Mode.Client) {
            RequestJoinEvent requestJoinEvent = Pools.obtain(RequestJoinEvent.class);
            NetDriver netDriver = getEngine().getSystem(NetDriver.class);
            netDriver.queueClientEvent(netDriver.getClientHostId(), requestJoinEvent, false);
        }
        else {
            joinPlayer(-1);
        }
    }

    public int getPlayerCount() {
        int count = 0;
        for (PlayerInfo playerInfo : playerInfos) {
            if (playerInfo.inPlay) count++;
        }
        return count;
    }

    private int getEmptyPlayerSlot() {
        for (int i = 0; i < playerInfos.length; ++i) {
            if (!playerInfos[i].inPlay) return i;
        }
        return -1;
    }

    private int addPlayer() {
        int playerId = getEmptyPlayerSlot();
        if (playerId == -1) {
            Log.info("Too many players");
            return -1;
        }

        playerInfos[playerId].inPlay = true;

        return playerId;
    }

    public void joinPlayer(int clientId) {
        if (matchState != MatchState.NotStarted) {
            Log.error("RefereeSystem", "Attempted a match that has already started/ended");
            return;
        }

        GameEngine engine = (GameEngine) getEngine();
        NetDriver netDriver = engine.getSystem(NetDriver.class);

        int playerId = -1;

        if (clientId == -1) {
            if (localPlayerId == -1) {
                // local server
                playerId = localPlayerId = addPlayer();
            }
        }
        else {
            // remote client
            ConnectionManager.ConnectionSlot slot = netDriver.getConnectionManager().getConnectionSlot(clientId);
            if (slot.getPlayerId() == -1) {
                playerId = addPlayer();
                if (playerId != -1) {
                    slot.setPlayerId(playerId);
                }
            }
            // send on first connect, reconnect, and if client somehow lost its playerId
            if (slot.getPlayerId() != -1) {
                assignPlayer(clientId, slot.getPlayerId());
            }
        }
        ConnectionStateRefreshEvent event = Pools.obtain(ConnectionStateRefreshEvent.class);
        netDriver.getEngine().getSystem(EventsSystem.class).queueEvent(event);

        if (playerId != -1) {
    //        spawnPlayerBall(playerId);
            PlayerJoinedEvent joinedEvent = Pools.obtain(PlayerJoinedEvent.class);
            joinedEvent.playerId = playerId;
            netDriver.queueClientEvent(-1, joinedEvent);
            engine.getSystem(EventsSystem.class).triggerEvent(joinedEvent);
        }
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

        PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
        pb.body.setTransform(pb.bodyDef.position.x, pb.bodyDef.position.y, 0);
        pb.body.setLinearVelocity(0, 0);

        modifyPlayerScore(playerId, -1);

        NetDriver netDriver = getEngine().getSystem(NetDriver.class);

        PlayerDeathEvent deathEvent = Pools.obtain(PlayerDeathEvent.class);
        deathEvent.playerId = playerId;
        deathEvent.entityId = EntityUtils.getId(entity);
        netDriver.queueClientEvent(-1, deathEvent);
        eventsSystem.queueEvent(deathEvent);

        ScoreBoardRefreshEvent scoreEvent = Pools.obtain(ScoreBoardRefreshEvent.class);
        netDriver.queueClientEvent(-1, scoreEvent);
        eventsSystem.queueEvent(scoreEvent);

        getEngine().removeEntity(entity);

        playerInfos[playerId].respawnTime = GameProperties.PLAYER_RESPAWN_TIME;
    }

    public PlayerInfo getPlayerInfo(int playerId) {
        return playerInfos[playerId];
    }

    public enum MatchState {
        NotStarted(0), Started(1), Ended(2);

        public final int value;

        MatchState(int value) {
            this.value = value;
        }
    }
}
