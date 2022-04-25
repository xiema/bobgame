package com.xam.bobgame;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.PooledEngine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pools;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.xam.bobgame.events.EventsSystem;
import com.xam.bobgame.events.PlayerControlEvent;
import com.xam.bobgame.game.ControlSystem;
import com.xam.bobgame.game.PhysicsSystem;

public class GameEngine extends PooledEngine {
    private EventsSystem eventsSystem;
    private GameDirector gameDirector;

    public GameEngine() {
        super();
    }

    public void initialize() {
        addSystem(eventsSystem = new EventsSystem(0));
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
    }

    private Vector2 tempVec = new Vector2();

    public void userInput(Viewport viewport) {
        PlayerControlEvent event = Pools.obtain(PlayerControlEvent.class);
        tempVec.set(Gdx.input.getX(), Gdx.input.getY());
        viewport.unproject(tempVec);
        event.x = tempVec.x;
        event.y = tempVec.y;
        event.controlId = 0;
        event.entityId = 0;
        eventsSystem.queueEvent(event);
    }
}
