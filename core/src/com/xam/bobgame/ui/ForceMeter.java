package com.xam.bobgame.ui;

import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.game.ControlSystem;
import com.xam.bobgame.game.PlayerControlInfo;
import com.xam.bobgame.game.PlayerInfo;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;

public class ForceMeter extends ProgressBar {

    private float forceValue = -1;
    private float stamina = 100;

    private GameEngine engine = null;

    public ForceMeter(Skin skin) {
        super(0, 100, 1, true, skin);
        getStyle().background.setMinWidth(50);
        getStyle().knobBefore.setMinWidth(50);
        setWidth(50);
    }

    @Override
    public void act(float delta) {
        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
        int playerId = refereeSystem.getLocalPlayerId();
        if (playerId != -1) {
            PlayerInfo playerInfo = refereeSystem.getPlayerInfo(playerId);
            stamina = playerInfo.stamina;
            PlayerControlInfo playerControlInfo = engine.getSystem(ControlSystem.class).getPlayerControlInfo(playerId);
            forceValue = (playerControlInfo.holdDuration * GameProperties.CHARGE_RATE) % (stamina * 2f);
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
    }
}
