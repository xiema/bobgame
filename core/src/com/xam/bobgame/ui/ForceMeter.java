package com.xam.bobgame.ui;

import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.ObjectMap;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameDirector;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.events.*;
import com.xam.bobgame.utils.MathUtils2;

public class ForceMeter extends ProgressBar {

    private float holdDuration = -1;
    private boolean buttonState = false;

    private GameEngine engine = null;

    public ForceMeter(Skin skin) {
        super(0, 100, 1, true, skin);
    }

    @Override
    public void act(float delta) {
        if (buttonState) {
            holdDuration = (Math.max(0, holdDuration) + GameProperties.SIMULATION_UPDATE_INTERVAL) % GameProperties.CHARGE_DURATION_2;
        }
        else {
            holdDuration = 0;
        }
        setValue(getMaxValue() * MathUtils2.mirror.apply(holdDuration / GameProperties.CHARGE_DURATION_2));
    }

    public void initialize(GameEngine engine) {
        this.engine = engine;
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);

        eventsSystem.addListener(PlayerControlEvent.class, new EventListenerAdapter<PlayerControlEvent>() {
            @Override
            public void handleEvent(PlayerControlEvent event) {
                if (event.controlId != ForceMeter.this.engine.getSystem(GameDirector.class).getLocalPlayerId()) return;
                if (event.buttonId != 0) return;
                buttonState = event.buttonState;
            }
        });
    }
}
