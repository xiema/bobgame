package com.xam.bobgame.ui;

import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.ObjectMap;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.events.*;
import com.xam.bobgame.game.PlayerInfo;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;

public class ForceMeter extends ProgressBar {

    private boolean buttonState = false;
    private float holdDuration = 0;
    private float forceValue = -1;
    private float stamina = 100;

    private GameEngine engine = null;

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    public ForceMeter(Skin skin) {
        super(0, 100, 1, true, skin);
        getStyle().background.setMinWidth(50);
        getStyle().knobBefore.setMinWidth(50);
        setWidth(50);

        listeners.put(PlayerControlEvent.class, new EventListenerAdapter<PlayerControlEvent>() {
            @Override
            public void handleEvent(PlayerControlEvent event) {
                if (event.buttonId > 0) return;
                if (event.controlId != engine.getSystem(RefereeSystem.class).getLocalPlayerId()) return;
                if (buttonState && !event.buttonState) {
                    Log.debug("Local Player (" + event.controlId + ") ButtonRelease " + holdDuration);
                    holdDuration = 0;
                }
                buttonState = event.buttonState;
            }
        });
        listeners.put(PlayerDeathEvent.class, new EventListenerAdapter<PlayerDeathEvent>() {
            @Override
            public void handleEvent(PlayerDeathEvent event) {
                if (event.playerId != engine.getSystem(RefereeSystem.class).getLocalPlayerId()) return;
                buttonState = false;
                holdDuration = 0;
            }
        });
    }

    @Override
    public void act(float delta) {
        if (buttonState) {
            holdDuration = ((holdDuration < 0 ? 0 : holdDuration) + GameProperties.SIMULATION_UPDATE_INTERVAL) % GameProperties.CHARGE_DURATION_2;
        }

        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
        int playerId = refereeSystem.getLocalPlayerId();
        if (playerId != -1) {
            PlayerInfo playerInfo = refereeSystem.getPlayerInfo(playerId);
            stamina = playerInfo.stamina;
            forceValue = (holdDuration * GameProperties.CHARGE_RATE) % (stamina * 2f);
        }
        else {
            forceValue = 0;
        }
        setHeight(stamina * 3);
        setRange(0, stamina);
        setValue(forceValue <= stamina ? forceValue : (2 * stamina - forceValue));
    }

    public void initialize(GameEngine engine) {
        this.engine = engine;
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
        eventsSystem.addListeners(listeners);
    }
}
