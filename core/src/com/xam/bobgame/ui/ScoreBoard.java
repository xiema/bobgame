package com.xam.bobgame.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectMap;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.events.*;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.net.NetDriver;

public class ScoreBoard extends Table {

    GameEngine engine;
    final Skin skin;

    private final Cell<?>[] playerNameCells = new Cell[NetDriver.MAX_CLIENTS];
    private final Cell<?>[] playerScoreCells = new Cell[NetDriver.MAX_CLIENTS];
    private final Label[] playerNameLabels = new Label[NetDriver.MAX_CLIENTS];
    private final Label[] playerScoreLabels = new Label[NetDriver.MAX_CLIENTS];

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    private int rowCount = 0;

    public ScoreBoard(Skin skin) {
        this.skin = skin;

        setBackground(skin.getDrawable("window"));

        columnDefaults(0).align(Align.left).fill().expand();
        columnDefaults(1).align(Align.right).fill().expand();

        for (int i = 0; i < playerNameLabels.length; ++i) {
            playerNameCells[i] = add(playerNameLabels[i] = new Label("a", skin));
            playerScoreCells[i] = add(playerScoreLabels[i] = new Label("a", skin));
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
//        listeners.put(PlayerScoreEvent.class, new EventListenerAdapter<PlayerScoreEvent>() {
//            @Override
//            public void handleEvent(PlayerScoreEvent event) {
//                Log.info("PlayerScoreEvent");
//                refreshPlayerScore(event.playerId);
//            }
//        });
        listeners.put(ScoreBoardRefreshEvent.class, new EventListenerAdapter<ScoreBoardRefreshEvent>() {
            @Override
            public void handleEvent(ScoreBoardRefreshEvent event) {
                refreshScoreBoard();
            }
        });
//        listeners.put(ScoreBoardUpdateEvent.class, new EventListenerAdapter<ScoreBoardUpdateEvent>() {
//            @Override
//            public void handleEvent(ScoreBoardUpdateEvent event) {
//                Log.info("ScoreBoardUpdateEvent");
//                refreshPlayerScore(event.playerId);
//            }
//        });
    }

    public void initialize(GameEngine engine) {
        this.engine = engine;
        engine.getSystem(EventsSystem.class).addListeners(listeners);
        refreshScoreBoard();
    }

    private void reposition() {
        setSize(250, 500);
        setPosition(GameProperties.WINDOW_WIDTH, GameProperties.WINDOW_HEIGHT, Align.topRight);
    }

    private void refreshScoreBoard() {
//        Log.info("refreshScoreBoard");
        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
        boolean[] playerExists = refereeSystem.getPlayerExists();
        int[] playerScores = refereeSystem.getPlayerScores();
        rowCount = 0;
        for (int i = 0; i < playerExists.length; ++i) {
            if (playerExists[i]) {
                addPlayer(i);
                setPlayerScore(i, playerScores[i]);
            }
        }

        for (int j = rowCount; j < playerNameCells.length; ++j) {
            playerNameCells[j].clearActor();
            playerScoreCells[j].clearActor();
        }
        reposition();
    }

    private void refreshPlayerScore(int playerId) {
        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
        if (playerScoreLabels[playerId] == null) return;
        playerScoreLabels[playerId].setText(refereeSystem.getPlayerScores()[playerId]);
    }

    public void addPlayer(int playerId) {
//        Log.info("addPlayer " + playerId);
        Label playerNameLabel = playerNameLabels[playerId];
        Label playerScoreLabel = playerScoreLabels[playerId];
        playerNameLabel.setText("PL." + playerId);
        playerScoreLabel.setText(0);
        playerNameCells[rowCount].setActor(playerNameLabel);
        playerScoreCells[rowCount].setActor(playerScoreLabel);
        rowCount++;
        reposition();
    }

    public void removePlayer(int playerId) {
//        Log.info("addPlayer " + playerId);
        Label playerNameLabel = playerNameLabels[playerId];
        Label playerScoreLabel = playerScoreLabels[playerId];
        getCell(playerNameLabel).clearActor();
        getCell(playerScoreLabel).clearActor();
        rowCount--;
        reposition();
    }

    public void setPlayerScore(int playerId, int score) {
//        Log.info("setPlayerScore " + playerId + " " + score);
        playerScoreLabels[playerId].setText(String.valueOf(score));
    }

//    @Override
//    public void act(float delta) {
//        super.act(delta);
//        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
//        boolean[] playerExists = refereeSystem.getPlayerExists();
//        int[] playerScores = refereeSystem.getPlayerScores();
//        for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
//            if (playerExists[i]) {
//                setPlayerScore(i, playerScores[i]);
//            }
//        }
//    }
}
