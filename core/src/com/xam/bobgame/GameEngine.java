package com.xam.bobgame;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.ai.AISystem;
import com.xam.bobgame.buffs.BuffSystem;
import com.xam.bobgame.components.IdentityComponent;
import com.xam.bobgame.definitions.GameDefinitions;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.events.*;
import com.xam.bobgame.events.classes.ConnectionStateRefreshEvent;
import com.xam.bobgame.events.classes.EntityCreatedEvent;
import com.xam.bobgame.events.classes.EntityDespawnedEvent;
import com.xam.bobgame.game.*;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.OrderedIntMap;

public class GameEngine extends PooledEngine {
    private static final Class<?>[] PAUSABLE_SYSTEMS = {
            ControlSystem.class,
            AISystem.class,
            PickupsSystem.class,
            BuffSystem.class,
            PhysicsSystem.class,
            HazardsSystem.class,
    };


    private BoBGame game;

    private Mode mode = Mode.None;

    EventsSystem eventsSystem;
    RefereeSystem refereeSystem;
    NetDriver netDriver;

    private int currentFrame = 0;
    private float currentTime = 0;

    private boolean stopping = false;

    private final OrderedIntMap<Entity> entityMap = new OrderedIntMap<>();

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    public GameEngine(BoBGame game) {
        super();
        this.game = game;

        game.inputMultiplexer.addProcessor(new InputAdapter() {
            private int activeButton = -1;

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                getSystem(ControlSystem.class).userInput(screenX, screenY, button, true);
                activeButton = button;
                return false;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                getSystem(ControlSystem.class).userInput(screenX, screenY, activeButton, false);
                activeButton = -1;
                return false;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                getSystem(ControlSystem.class).userInput(screenX, screenY, activeButton, true);
                return false;
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                getSystem(ControlSystem.class).userInput(screenX, screenY, activeButton, false);
                activeButton = -1;
                return false;
            }
        });

        addEntityListener(Family.all().get(), 255, new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
                ComponentMappers.identity.get(entity).spawning = false;
            }

            @Override
            public void entityRemoved(Entity entity) {

            }
        });
    }

    public void initialize() {
        addSystem(eventsSystem = new EventsSystem(1));
        addSystem(netDriver = new NetDriver(0));
        addSystem(refereeSystem = new RefereeSystem(10));
        addSystem(new ControlSystem(20));
        addSystem(new AISystem(30));
        addSystem(new PickupsSystem(40));
        addSystem(new BuffSystem(50));
        addSystem(new PhysicsSystem(60));
        addSystem(new HazardsSystem(70));

        pauseSystems();

        eventsSystem.addListeners(listeners);
    }

    @Override
    public void update(float deltaTime) {
        if (stopping) {
            stopInternal();
            for (EntitySystem system : getSystems()) system.setProcessing(true);
            pauseSystems();
            return;
        }
        super.update(deltaTime);
        netDriver.update2(deltaTime);
        currentFrame++;
        currentTime += Gdx.graphics.getDeltaTime();
    }

    private void stopInternal() {
        // remove systems
        Array<EntitySystem> systems = new Array<>();
        removeSystem(netDriver);
        removeSystem(refereeSystem);
        for (EntitySystem system : getSystems()) {
            if (system != eventsSystem) {
                systems.add(system);
            }
        }
        for (EntitySystem system : systems) removeSystem(system);
        removeSystem(eventsSystem);

        addSystem(eventsSystem);
        addSystem(netDriver);
        addSystem(refereeSystem);
        for (EntitySystem system : systems) addSystem(system);

        currentTime = 0;
        currentFrame = 0;
        stopping = false;

        eventsSystem.addListeners(listeners);
        game.onEngineStarted();
    }

    public void stop() {
        removeAllEntities();
        stopping = true;
        for (EntitySystem system : getSystems()) system.setProcessing(false);
    }

    public void start() {
        if (mode == Mode.Server) refereeSystem.setupGame();
        resumeSystems();
        eventsSystem.queueEvent(Pools.obtain(ConnectionStateRefreshEvent.class));
    }

    public void pauseSystems() {
        for (Class<?> clazz : PAUSABLE_SYSTEMS) {
            //noinspection unchecked
            getSystem((Class<? extends EntitySystem>) clazz).setProcessing(false);
        }
    }

    public void resumeSystems() {
        for (Class<?> clazz : PAUSABLE_SYSTEMS) {
            //noinspection unchecked
            getSystem((Class<? extends EntitySystem>) clazz).setProcessing(true);
        }
    }

    @Override
    public void addEntity(Entity entity) {
        super.addEntity(entity);
        int entityID = EntityUtils.getId(entity);
        entityMap.put(entityID, entity);
        if (mode == Mode.Server) {
            EntityCreatedEvent netEvent = Pools.obtain(EntityCreatedEvent.class);
            netEvent.entityId = entityID;
            netDriver.queueClientEvent(-1, netEvent, false);
        }
    }

    @Override
    public void removeEntity(Entity entity) {
        IdentityComponent iden = ComponentMappers.identity.get(entity);
        iden.despawning = true;
        int entityID = iden.id;
        entityMap.remove(entityID);
        if (mode == Mode.Server) {
            EntityDespawnedEvent event = Pools.obtain(EntityDespawnedEvent.class);
            event.entityId = EntityUtils.getId(entity);
            netDriver.queueClientEvent(-1, event, false);
        }
        super.removeEntity(entity);
    }

    @Override
    public void removeAllEntities() {
        super.removeAllEntities();
        entityMap.clear();
    }

    public void setMode(Mode mode) {
        Log.debug("GameEngine mode set to " + mode);
        this.mode = mode;
    }

    public void setupServer() {
        mode = Mode.Server;
        getSystem(ControlSystem.class).setControlFacing(true);
    }

    public void setupClient() {
        mode = Mode.Client;
        getSystem(ControlSystem.class).setControlFacing(false);
        netDriver.setupClient();
//        getSystem(PhysicsSystem.class).setForceFactor(NetDriver.FORCE_FACTOR);
    }

    public Mode getMode() {
        return mode;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public float getCurrentTime() {
        return currentTime;
    }

    public Entity getEntityById(int entityId) {
        Entity entity = entityMap.get(entityId, null);
        if (entity == null) return null;
        if (!(EntityUtils.isAdded(entity))) return null;
        return entity;
    }

    public OrderedIntMap<Entity> getEntityMap() {
        return entityMap;
    }

    public GameDefinitions getGameDefinitions() {
        return game.gameDefinitions;
    }

    public Viewport getViewport() {
        return game.getWorldViewport();
    }

    public enum Mode {
        Client, Server, None
    }
}
