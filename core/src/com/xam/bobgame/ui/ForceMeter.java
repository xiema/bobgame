package com.xam.bobgame.ui;

import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.xam.bobgame.game.ControlSystem;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.utils.MathUtils2;

public class ForceMeter extends ProgressBar {

    private float holdDuration = -1;

    private GameEngine engine = null;

    public ForceMeter(Skin skin) {
        super(0, 100, 1, true, skin);
        getStyle().background.setMinWidth(50);
        getStyle().knobBefore.setMinWidth(50);
        setWidth(50);
    }

    @Override
    public void act(float delta) {
        int playerId = engine.getSystem(RefereeSystem.class).getLocalPlayerId();
        if (playerId != -1 && engine.getSystem(ControlSystem.class).getPlayerControlInfo(playerId).buttonState) {
            holdDuration = (Math.max(0, holdDuration) + GameProperties.SIMULATION_UPDATE_INTERVAL) % GameProperties.CHARGE_DURATION_2;
        }
        else {
            holdDuration = 0;
        }
        setValue(getMaxValue() * MathUtils2.mirror.apply(holdDuration / GameProperties.CHARGE_DURATION_2));
    }

    public void initialize(GameEngine engine) {
        this.engine = engine;
    }
}
