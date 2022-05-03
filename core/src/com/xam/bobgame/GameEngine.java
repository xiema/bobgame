package com.xam.bobgame;

import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pools;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.events.*;
import com.xam.bobgame.game.ControlSystem;
import com.xam.bobgame.game.PhysicsSystem;
import com.xam.bobgame.net.NetDriver;

public class GameEngine extends PooledEngine {
    private EventsSystem eventsSystem;
    private GameDirector gameDirector;
    private NetDriver netDriver;

    private int lastSnapshot = -1;

    private int currentFrame = 0;
    private float simulationTime = 0;
    public static final float SIM_STEP_SIZE = 1.0f / 60f;

//    SharedMemoryChecker memCheck = new SharedMemoryChecker("check.txt");

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    public GameEngine() {
        super();
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
        addSystem(gameDirector = new GameDirector(10));
        addSystem(new ControlSystem(20));
        addSystem(new PhysicsSystem(30));

        eventsSystem.addListeners(listeners);
    }

    public void gameSetup() {
        gameDirector.setupGame();
    }

    @Override
    public void update(float deltaTime) {
        simulationTime += SIM_STEP_SIZE;
        super.update(deltaTime);
        netDriver.update2();
//        if (netDriver.getMode() == NetDriver.Mode.Server) {
//            netDriver.syncClients(deltaTime);
//        }
//        if (netDriver.getMode() == NetDriver.Mode.Server) {
//            Entity entity = gameDirector.getEntityById(0);
//            memCheck.pushShared(entity, currentFrame);
//        }

        currentFrame++;
    }

    private final Vector2 tempVec = new Vector2();

    public void restart() {
        removeAllEntities();

        Array<EntitySystem> systems = new Array<>();
        removeSystem(netDriver);
        removeSystem(eventsSystem);
        removeSystem(gameDirector);
        for (EntitySystem system : getSystems()) systems.add(system);

        addSystem(netDriver);
        addSystem(eventsSystem);
        addSystem(gameDirector);
        for (EntitySystem system : systems) addSystem(system);

        eventsSystem.addListeners(listeners);
    }

    public void addInputProcessor(InputMultiplexer inputMultiplexer, final Viewport viewport) {
        inputMultiplexer.addProcessor(new InputAdapter() {
            public void userInput(int x, int y, int button, boolean state) {
                if (gameDirector.getLocalPlayerEntityId() == -1) return;

                PlayerControlEvent event = Pools.obtain(PlayerControlEvent.class);
                tempVec.set(x, y);
                viewport.unproject(tempVec);
                event.x = tempVec.x;
                event.y = tempVec.y;
                event.buttonId = button;
                event.buttonState = state;
                event.controlId = gameDirector.getLocalPlayerId();
                event.entityId = gameDirector.getLocalPlayerEntityId();

                if (netDriver.getMode() == NetDriver.Mode.Client && netDriver.getClient().isConnected()) {
                    PlayerControlEvent netEvent = Pools.obtain(PlayerControlEvent.class);
                    event.copyTo(netEvent);
                    netDriver.queueClientEvent(-1, netEvent);
                }

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

//            @Override
//            public boolean touchDragged(int screenX, int screenY, int pointer) {
//                userInput(screenX, screenY, -1, true);
//                return true;
//            }

//            @Override
//            public boolean mouseMoved(int screenX, int screenY) {
//                userInput(screenX, screenY, -1, false);
//                return false;
//            }
        });
    }

    public void setMode(NetDriver.Mode mode) {
        if (mode == NetDriver.Mode.Server) {
            netDriver.setMode(NetDriver.Mode.Server);
            netDriver.getServer().start(NetDriver.PORT_TCP, NetDriver.PORT_UDP);
            getSystem(ControlSystem.class).setEnabled(true);
            getSystem(PhysicsSystem.class).setEnabled(true);
            getSystem(PhysicsSystem.class).setPosIterations(2);
            getSystem(PhysicsSystem.class).setVelIterations(6);
            gameSetup();
            getSystem(GameDirector.class).getLocalPlayerEntity().getComponent(PhysicsBodyComponent.class).body.applyForceToCenter(MathUtils.random() * 1000f, MathUtils.random() * 100f, true);
        }
        else {
            getSystem(PhysicsSystem.class).setForceFactor(NetDriver.FORCE_FACTOR);
        }
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

//    public SharedMemoryChecker getMemCheck() {
//        return memCheck;
//    }
}
