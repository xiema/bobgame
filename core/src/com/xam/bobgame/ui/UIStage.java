
package com.xam.bobgame.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.xam.bobgame.BoBGame;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.events.*;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.GameProfile;

public class UIStage extends Stage {

    final BoBGame game;
    final GameEngine engine;
    final RefereeSystem refereeSystem;
    final NetDriver netDriver;
    final Skin skin;

    final Label bitrateLabel;

    final MainMenu mainMenu;

    final Table scoreTable;
    final ForceMeter forceMeter;

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    private Label[] playerNameLabels = new Label[NetDriver.MAX_CLIENTS];
    private Label[] playerScoreLabels = new Label[NetDriver.MAX_CLIENTS];

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

        mainMenu = new MainMenu(game, skin);
        mainMenu.setPosition(0, GameProperties.WINDOW_HEIGHT, Align.topLeft);
        addActor(mainMenu);

        forceMeter = new ForceMeter(skin);
        forceMeter.setPosition(GameProperties.WINDOW_WIDTH, 0, Align.bottomRight);
        addActor(forceMeter);

        scoreTable = new Table();
        scoreTable.columnDefaults(0).align(Align.left).width(200);
        scoreTable.columnDefaults(1).align(Align.right).width(200);
        scoreTable.setPosition(GameProperties.WINDOW_WIDTH, GameProperties.WINDOW_HEIGHT, Align.topRight);
        addActor(scoreTable);
        for (int i = 0; i < playerNameLabels.length; ++i) {
            playerNameLabels[i] = new Label("", skin);
            playerScoreLabels[i] = new Label("", skin);
        }

        ((InputMultiplexer) Gdx.input.getInputProcessor()).addProcessor(this);
    }

    public void initialize(GameEngine engine) {
        forceMeter.initialize(engine);
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
        eventsSystem.addListener(PlayerJoinedEvent.class, new EventListenerAdapter<PlayerJoinedEvent>() {
            @Override
            public void handleEvent(PlayerJoinedEvent event) {
                addPlayer(event.playerId);
            }
        });
        eventsSystem.addListener(ScoreBoardRefreshEvent.class, new EventListenerAdapter<ScoreBoardRefreshEvent>() {
            @Override
            public void handleEvent(ScoreBoardRefreshEvent event) {
                refreshScoreBoard();
            }
        });
        eventsSystem.addListener(ScoreBoardUpdateEvent.class, new EventListenerAdapter<ScoreBoardUpdateEvent>() {
            @Override
            public void handleEvent(ScoreBoardUpdateEvent event) {
                refreshPlayerScore(event.playerId);
            }
        });
        mainMenu.refreshElementStates();
    }

    private void refreshScoreBoard() {
        scoreTable.clear();
        RefereeSystem refereeSystem = game.getEngine().getSystem(RefereeSystem.class);
        int[] playerControlMap = refereeSystem.getPlayerControlMap();
        int[] playerScores = refereeSystem.getPlayerScores();
        for (int i = 0; i < playerControlMap.length; ++i) {
            if (playerControlMap[i] != -1) {
                addPlayer(i);
                setPlayerScore(i, playerScores[i]);
            }
        }
    }

    private void refreshPlayerScore(int playerId) {
        RefereeSystem refereeSystem = game.getEngine().getSystem(RefereeSystem.class);
        if (playerScoreLabels[playerId] == null) return;
        playerScoreLabels[playerId].setText(refereeSystem.getPlayerScores()[playerId]);
    }

    public void addPlayer(int playerId) {
        Label playerNameLabel = playerNameLabels[playerId];
        Label playerScoreLabel = playerScoreLabels[playerId];
        playerNameLabel.setText("PL." + playerId);
        playerScoreLabel.setText(0);
        scoreTable.add(playerNameLabel);
        scoreTable.add(playerScoreLabel);
        scoreTable.row();

        scoreTable.setPosition(GameProperties.WINDOW_WIDTH - 50, GameProperties.WINDOW_HEIGHT - 50, Align.topRight);
    }

    public void setPlayerScore(int playerId, int score) {
        playerScoreLabels[playerId].setText(String.valueOf(score));
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        RefereeSystem refereeSystem = game.getEngine().getSystem(RefereeSystem.class);
        boolean[] playerExists = refereeSystem.getPlayerExists();
        int[] playerScores = refereeSystem.getPlayerScores();
        for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
            if (playerExists[i]) {
                setPlayerScore(i, playerScores[i]);
            }
        }
    }

    @Override
    public void draw() {
        super.draw();
    }
}
