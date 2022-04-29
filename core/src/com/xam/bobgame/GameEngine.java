package com.xam.bobgame;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pools;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.xam.bobgame.events.EventsSystem;
import com.xam.bobgame.events.PlayerControlEvent;
import com.xam.bobgame.game.ControlSystem;
import com.xam.bobgame.game.PhysicsSystem;
import com.xam.bobgame.net.NetDriver;

public class GameEngine extends PooledEngine {
    private EventsSystem eventsSystem;
    private GameDirector gameDirector;
    private NetDriver netDriver;

    private int lastSnapshot = -1;

    public GameEngine() {
        super();
    }

    public void initialize() {
        addSystem(netDriver = new NetDriver(0));
        addSystem(eventsSystem = new EventsSystem(1));
        addSystem(gameDirector = new GameDirector(10));
        addSystem(new ControlSystem(20));
        addSystem(new PhysicsSystem(30));
    }

    public void gameSetup() {
        gameDirector.setupGame();
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        netDriver.updateDropped();
        if (netDriver.getMode() == NetDriver.Mode.Server) {
            netDriver.syncClients(deltaTime);
        }
    }

    private final Vector2 tempVec = new Vector2();

    public void addInputProcessor(InputMultiplexer inputMultiplexer, final Viewport viewport) {
        inputMultiplexer.addProcessor(new InputAdapter() {
            public void userInput(int x, int y, int button, boolean state) {
                PlayerControlEvent event = Pools.obtain(PlayerControlEvent.class);
                tempVec.set(x, y);
                viewport.unproject(tempVec);
                event.x = tempVec.x;
                event.y = tempVec.y;
                event.buttonId = button;
                event.buttonState = state;
                event.controlId = gameDirector.getLocalPlayerId();
                event.entityId = gameDirector.getPlayerEntityId();

                if (netDriver.getMode() == NetDriver.Mode.Client) {
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

//            @Override
//            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
//                userInput(screenX, screenY, button, false);
//                return false;
//            }
//
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

    public int getLastSnapshotFrame() {
        return lastSnapshot;
    }

    public int setLastSnapshotFrame() {
        return lastSnapshot++;
    }
}
