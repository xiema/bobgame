
package com.xam.bobgame.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.xam.bobgame.BoBGame;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.events.*;
import com.xam.bobgame.net.NetDriver;

public class UIStage extends Stage {

    final BoBGame game;
    final GameEngine engine;
    final RefereeSystem refereeSystem;
    final NetDriver netDriver;
    final Skin skin;

    final Label bitrateLabel;

    final MainMenu mainMenu;

    final ScoreBoard scoreBoard;
    final ForceMeter forceMeter;

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    public UIStage(final BoBGame game, Viewport viewport, Batch batch, Skin skin) {
        super(viewport, batch);
        this.game = game;
        this.skin = skin;
        engine = game.getEngine();
        netDriver = engine.getSystem(NetDriver.class);
        refereeSystem = engine.getSystem(RefereeSystem.class);

        bitrateLabel = new Label("0", skin) {
            @Override
            public void act(float delta) {
                super.act(delta);
                setText(String.valueOf(netDriver.getAverageBitrate()));
            }
        };
        bitrateLabel.setPosition(0, 0, Align.bottomLeft);
        addActor(bitrateLabel);

        mainMenu = new MainMenu(skin);
        mainMenu.setPosition(0, GameProperties.WINDOW_HEIGHT, Align.topLeft);
        addActor(mainMenu);

        forceMeter = new ForceMeter(skin);
        forceMeter.setPosition(350, 0, Align.bottomRight);
        addActor(forceMeter);

        scoreBoard = new ScoreBoard(skin);
        addActor(scoreBoard);

        ((InputMultiplexer) Gdx.input.getInputProcessor()).addProcessor(this);
    }

    public void initialize(GameEngine engine) {
        forceMeter.initialize(engine);
        mainMenu.initialize(engine);
        scoreBoard.initialize(engine);
    }
}
