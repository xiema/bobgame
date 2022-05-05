package com.xam.bobgame;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.ai.AISystem;
import com.xam.bobgame.definitions.GameDefinitions;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.events.*;
import com.xam.bobgame.game.*;
import com.xam.bobgame.net.NetDriver;

import java.util.Arrays;

public class GameEngine extends PooledEngine {
    private static final Class<?>[] PAUSABLE_SYSTEMS = {
            ControlSystem.class,
            AISystem.class,
            PickupsSystem.class,
            PhysicsSystem.class,
            HazardsSystem.class,
    };


    private BoBGame game;

    private EventsSystem eventsSystem;
    private RefereeSystem refereeSystem;
    private NetDriver netDriver;

    private int lastSnapshot = -1;

    private int currentFrame = 0;
    private float simulationTime = 0;
    public static final float SIM_STEP_SIZE = 1.0f / 60f;

    private boolean restarting = false;

    private final IntMap<Entity> entityMap = new IntMap<>();
    private final IntArray sortedEntityIds = new IntArray(true, 4);

//    SharedMemoryChecker memCheck = new SharedMemoryChecker("check.txt");

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    public GameEngine(BoBGame game) {
        super();
        this.game = game;
        listeners.put(DisconnectEvent.class, new EventListenerAdapter<DisconnectEvent>() {
            @Override
            public void handleEvent(DisconnectEvent event) {
                restart();
            }
        });
    }

    public void initialize() {
        addSystem(netDriver = new NetDriver(0));
        addSystem(eventsSystem = new EventsSystem(1));
        addSystem(refereeSystem = new RefereeSystem(10));
        addSystem(new ControlSystem(20));
        addSystem(new AISystem(30));
        addSystem(new PickupsSystem(40));
        addSystem(new PhysicsSystem(50));
        addSystem(new HazardsSystem(60));

        pauseGame();

        eventsSystem.addListeners(listeners);
    }

    @Override
    public void update(float deltaTime) {
        if (restarting) {
            Array<EntitySystem> systems = new Array<>();
            removeSystem(netDriver);
            removeSystem(eventsSystem);
            removeSystem(refereeSystem);
            for (EntitySystem system : getSystems()) systems.add(system);
            Log.info("All systems removed");

            addSystem(netDriver);
            addSystem(eventsSystem);
            addSystem(refereeSystem);
            for (EntitySystem system : systems) addSystem(system);

            for (EntitySystem system : getSystems()) system.setProcessing(true);
            pauseGame();

            eventsSystem.addListeners(listeners);

            game.onEngineStarted();

            restarting = false;
            return;
        }
        simulationTime += SIM_STEP_SIZE;
        super.update(deltaTime);
        netDriver.update2();
        currentFrame++;
    }

    private final Vector2 tempVec = new Vector2();

    public void restart() {
        removeAllEntities();
        restarting = true;

        for (EntitySystem system : getSystems()) system.setProcessing(false);
    }

    public void start() {
        refereeSystem.setupGame();
        resumeGame();
    }

    public void pauseGame() {
        for (Class<?> clazz : PAUSABLE_SYSTEMS) {
            //noinspection unchecked
            getSystem((Class<? extends EntitySystem>) clazz).setProcessing(false);
        }
    }

    public void resumeGame() {
        for (Class<?> clazz : PAUSABLE_SYSTEMS) {
            //noinspection unchecked
            getSystem((Class<? extends EntitySystem>) clazz).setProcessing(true);
        }
    }

    public void addInputProcessor(InputMultiplexer inputMultiplexer, final Viewport viewport) {
        inputMultiplexer.addProcessor(new InputAdapter() {
            public void userInput(int x, int y, int button, boolean state) {
                if (refereeSystem.getLocalPlayerEntityId() == -1) return;

                PlayerControlEvent event = Pools.obtain(PlayerControlEvent.class);
                tempVec.set(x, y);
                viewport.unproject(tempVec);
                event.x = tempVec.x;
                event.y = tempVec.y;
                event.buttonId = button;
                event.buttonState = state;
                event.controlId = refereeSystem.getLocalPlayerId();
                event.entityId = refereeSystem.getLocalPlayerEntityId();

                netDriver.queueClientEvent(-1, event);
                eventsSystem.queueEvent(event);
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                userInput(screenX, screenY, button, true);
                return false;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                userInput(screenX, screenY, button, false);
                return false;
            }

            @Override
            public boolean touchDragged(int screenX, int screenY, int pointer) {
                userInput(screenX, screenY, 0, true);
                return false;
            }

            @Override
            public boolean mouseMoved(int screenX, int screenY) {
                userInput(screenX, screenY, 0, false);
                return false;
            }
        });
    }

    @Override
    public void addEntity(Entity entity) {
        super.addEntity(entity);
        int entityID = EntityUtils.getId(entity);
        Entity old = entityMap.put(entityID, entity);
        if (old == null) {
            int ins = Arrays.binarySearch(sortedEntityIds.items, 0, sortedEntityIds.size, entityID);
            sortedEntityIds.insert(ins >= 0 ? ins : -(ins + 1), entityID);
        }

        if (netDriver.getMode() == NetDriver.Mode.Server) {
            EntityCreatedEvent netEvent = Pools.obtain(EntityCreatedEvent.class);
            netEvent.entityId = entityID;
            netDriver.queueClientEvent(-1, netEvent, false);
        }
    }

    @Override
    public void removeEntityInternal(Entity entity) {
        int entityID = EntityUtils.getId(entity);
        Entity old = entityMap.remove(entityID);
        if (old != null) {
            int rem = Arrays.binarySearch(sortedEntityIds.items, 0, sortedEntityIds.size, entityID);
            if (sortedEntityIds.get(rem) == entityID) {
                sortedEntityIds.removeIndex(rem);
            }
        }
        super.removeEntityInternal(entity);
    }

    @Override
    public void removeEntity(Entity entity) {
        if (netDriver.getMode() == NetDriver.Mode.Server) {
            EntityDespawnedEvent event = Pools.obtain(EntityDespawnedEvent.class);
            event.entityId = EntityUtils.getId(entity);
            netDriver.queueClientEvent(-1, event, false);
        }
        super.removeEntity(entity);
    }

    public void setupServer() {
        refereeSystem.setEnabled(true);
        getSystem(ControlSystem.class).setEnabled(true);
        getSystem(ControlSystem.class).setControlFacing(true);
        getSystem(PhysicsSystem.class).setEnabled(true);
        getSystem(HazardsSystem.class).setEnabled(true);
        getSystem(PickupsSystem.class).setEnabled(true);
    }

    public void setupClient() {
        refereeSystem.setEnabled(false);
        getSystem(ControlSystem.class).setEnabled(true);
        getSystem(ControlSystem.class).setControlFacing(false);
        getSystem(PhysicsSystem.class).setEnabled(false);
        getSystem(HazardsSystem.class).setEnabled(false);
        getSystem(PickupsSystem.class).setEnabled(false);
//        getSystem(PhysicsSystem.class).setForceFactor(NetDriver.FORCE_FACTOR);
    }

    public NetDriver.Mode getMode() {
        return netDriver.getMode();
    }

    public int getLastSnapshotFrame() {
        return lastSnapshot;
    }

    public int setLastSnapshotFrame() {
        return lastSnapshot++;
    }

    public int getCurrentFrame() {
        return currentFrame;
    }

    public float getSimulationTime() {
        return simulationTime;
    }

    public Entity getEntityById(int entityId) {
        Entity entity = entityMap.get(entityId, null);
        if (entity == null) return null;
        if ((entity.isRemoving() || entity.isScheduledForRemoval())) return null;
        return entity;
    }

    public IntMap<Entity> getEntityMap() {
        return entityMap;
    }

    public IntArray getSortedEntityIds() {
        return sortedEntityIds;
    }

    public GameDefinitions getGameDefinitions() {
        return game.gameDefinitions;
    }

    //    public SharedMemoryChecker getMemCheck() {
//        return memCheck;
//    }
}
