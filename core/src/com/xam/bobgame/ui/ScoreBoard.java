package com.xam.bobgame.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectMap;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.events.*;
import com.xam.bobgame.events.classes.PlayerJoinedEvent;
import com.xam.bobgame.events.classes.PlayerLeftEvent;
import com.xam.bobgame.events.classes.ScoreBoardRefreshEvent;
import com.xam.bobgame.game.PlayerInfo;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.net.NetDriver;

public class ScoreBoard extends Table {

    GameEngine engine;
    final Skin skin;

    private final Cell<?>[] playerNameCells = new Cell[NetDriver.MAX_CLIENTS];
    private final Cell<?>[] playerScoreCells = new Cell[NetDriver.MAX_CLIENTS];
    private final Cell<?>[] playerRespawnTimeCells = new Cell[NetDriver.MAX_CLIENTS];
    private final Label[] playerNameLabels = new Label[NetDriver.MAX_CLIENTS];
    private final Label[] playerScoreLabels = new Label[NetDriver.MAX_CLIENTS];
    private final Label[] playerRespawnTimeLabels = new Label[NetDriver.MAX_CLIENTS];

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    private int rowCount = 0;

    public ScoreBoard(Skin skin) {
        this.skin = skin;

        setBackground(skin.getDrawable("window"));

        columnDefaults(0).align(Align.left).fill().expand();
        columnDefaults(1).align(Align.right).fill().expand();

        for (int i = 0; i < playerNameLabels.length; ++i) {
            playerNameCells[i] = add(playerNameLabels[i] = new Label("", skin));
            playerScoreCells[i] = add(playerScoreLabels[i] = new Label("", skin));
            playerRespawnTimeCells[i] = add(playerRespawnTimeLabels[i] = new Label("", skin));
            row();
        }

        listeners.put(PlayerJoinedEvent.class, new EventListenerAdapter<PlayerJoinedEvent>() {
            @Override
            public void handleEvent(PlayerJoinedEvent event) {
                addPlayer(event.playerId);
            }
        });
        listeners.put(PlayerLeftEvent.class, new EventListenerAdapter<PlayerLeftEvent>() {
            @Override
            public void handleEvent(PlayerLeftEvent event) {
                removePlayer(event.playerId);
            }
        });
        listeners.put(ScoreBoardRefreshEvent.class, new EventListenerAdapter<ScoreBoardRefreshEvent>() {
            @Override
            public void handleEvent(ScoreBoardRefreshEvent event) {
                refreshScoreBoard();
            }
        });
    }

    public void initialize(GameEngine engine) {
        this.engine = engine;
        engine.getSystem(EventsSystem.class).addListeners(listeners);
        refreshScoreBoard();
    }

    private void reposition() {
        setSize(350, 500);
        setPosition(GameProperties.WINDOW_WIDTH, GameProperties.WINDOW_HEIGHT, Align.topRight);
    }

    private void refreshScoreBoard() {
        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
        rowCount = 0;
        for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
            PlayerInfo playerInfo = refereeSystem.getPlayerInfo(i);
            if (playerInfo.inPlay) {
                addPlayer(i);
                setPlayerScore(i, playerInfo.score, playerInfo.respawnTime);
            }
        }

        for (int j = rowCount; j < playerNameCells.length; ++j) {
            playerNameCells[j].clearActor();
            playerScoreCells[j].clearActor();
            playerRespawnTimeCells[j].clearActor();
        }
        reposition();
    }

    public void addPlayer(int playerId) {
        Label playerNameLabel = playerNameLabels[playerId];
        Label playerScoreLabel = playerScoreLabels[playerId];
        Label playerRespawnTimeLabel = playerRespawnTimeLabels[playerId];
        playerNameLabel.setText("PL." + playerId);
        playerScoreLabel.setText(0);
        playerNameCells[rowCount].setActor(playerNameLabel);
        playerScoreCells[rowCount].setActor(playerScoreLabel);
        playerRespawnTimeCells[rowCount].setActor(playerRespawnTimeLabel);
        rowCount++;
        reposition();
    }

    public void removePlayer(int playerId) {
        Label playerNameLabel = playerNameLabels[playerId];
        Label playerScoreLabel = playerScoreLabels[playerId];
        Label playerRespawnTimeLabel = playerRespawnTimeLabels[playerId];
        getCell(playerNameLabel).clearActor();
        getCell(playerScoreLabel).clearActor();
        getCell(playerRespawnTimeLabel).clearActor();
        rowCount--;
        reposition();
    }

    public void setPlayerScore(int playerId, int score, float respawnTime) {
        playerScoreLabels[playerId].setText(String.valueOf(score));
        playerRespawnTimeLabels[playerId].setText(String.valueOf((int) respawnTime));
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
        for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
            PlayerInfo playerInfo = refereeSystem.getPlayerInfo(i);
            if (playerInfo.inPlay) {
                setPlayerScore(i, playerInfo.score, playerInfo.respawnTime);
            }
        }
    }
}
