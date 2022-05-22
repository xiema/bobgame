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
import com.xam.bobgame.events.classes.PlayerAssignEvent;
import com.xam.bobgame.events.classes.PlayerJoinedEvent;
import com.xam.bobgame.events.classes.PlayerLeftEvent;
import com.xam.bobgame.events.classes.ScoreBoardRefreshEvent;
import com.xam.bobgame.game.PlayerInfo;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.net.NetDriver;

import java.util.Formatter;

public class ScoreBoard extends Table {

    GameEngine engine;
    final Skin skin;

    private final Cell<?>[] playerNameCells = new Cell[NetDriver.MAX_CLIENTS];
    private final Cell<?>[] playerScoreCells = new Cell[NetDriver.MAX_CLIENTS];
    private final Cell<?>[] playerRespawnTimeCells = new Cell[NetDriver.MAX_CLIENTS];
    private final Cell<?>[] playerLatencyCells = new Cell[NetDriver.MAX_CLIENTS];
    private final Label[] playerNameLabels = new Label[NetDriver.MAX_CLIENTS];
    private final Label[] playerScoreLabels = new Label[NetDriver.MAX_CLIENTS];
    private final Label[] playerRespawnTimeLabels = new Label[NetDriver.MAX_CLIENTS];
    private final Label[] playerLatencyLabels = new Label[NetDriver.MAX_CLIENTS];

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    private int rowCount = 0;

    public ScoreBoard(Skin skin) {
        this.skin = skin;

        setBackground(skin.getDrawable("window"));

        columnDefaults(0).align(Align.left).fill().expand();
        columnDefaults(1).align(Align.right).fill().expand();
        columnDefaults(2).align(Align.center).fill().expand();
        columnDefaults(3).align(Align.right).fill().expand();

        for (int i = 0; i < playerNameLabels.length; ++i) {
            playerNameCells[i] = add(playerNameLabels[i] = new Label("", skin));
            playerScoreCells[i] = add(playerScoreLabels[i] = new Label("", skin));
            playerRespawnTimeCells[i] = add(playerRespawnTimeLabels[i] = new Label("", skin));
            playerLatencyCells[i] = add(playerLatencyLabels[i] = new Label("", skin));

            playerRespawnTimeLabels[i].setAlignment(Align.center);
            playerScoreLabels[i].setAlignment(Align.right);
            playerLatencyLabels[i].setAlignment(Align.right);
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
        listeners.put(PlayerAssignEvent.class, new EventListenerAdapter<PlayerAssignEvent>() {
            @Override
            public void handleEvent(PlayerAssignEvent event) {
                setPlayerStyle(event.playerId, "defaultWhite");
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
        for (int i = 0; i < playerNameLabels.length; ++i) setPlayerStyle(i, "default");
    }

    private void reposition() {
        setSize(350, 500);
        setPosition(GameProperties.WINDOW_WIDTH, GameProperties.WINDOW_HEIGHT, Align.topRight);
    }

    private void setPlayerStyle(int playerId, String styleName) {
        if (!skin.has(styleName, Label.LabelStyle.class)) {
            Log.error("Skin has no LabelStyle named " + styleName);
            return;
        }
        Label.LabelStyle style = skin.get(styleName, Label.LabelStyle.class);
        playerNameLabels[playerId].setStyle(style);
        playerScoreLabels[playerId].setStyle(style);
        playerRespawnTimeLabels[playerId].setStyle(style);
        playerLatencyLabels[playerId].setStyle(style);
    }

    private void refreshScoreBoard() {
        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
        if (refereeSystem == null) return;

        for (int j = 0; j < playerNameCells.length; ++j) {
            playerNameCells[j].clearActor();
            playerScoreCells[j].clearActor();
            playerRespawnTimeCells[j].clearActor();
            playerLatencyCells[j].clearActor();
        }

        rowCount = 0;
        for (PlayerInfo playerInfo : refereeSystem.getSortedPlayerInfos()) {
            addPlayer(playerInfo.playerId);
            setPlayerScore(playerInfo.playerId, playerInfo.score, playerInfo.respawnTime, playerInfo.latency);
        }

        reposition();
    }

    public void addPlayer(int playerId) {
        Label playerNameLabel = playerNameLabels[playerId];
        Label playerScoreLabel = playerScoreLabels[playerId];
        Label playerRespawnTimeLabel = playerRespawnTimeLabels[playerId];
        Label playerLatencyLabel = playerLatencyLabels[playerId];
        playerNameLabel.setText("PL." + playerId);
        playerScoreLabel.setText(0);
        playerNameCells[rowCount].setActor(playerNameLabel);
        playerScoreCells[rowCount].setActor(playerScoreLabel);
        playerRespawnTimeCells[rowCount].setActor(playerRespawnTimeLabel);
        playerLatencyCells[rowCount].setActor(playerLatencyLabel);
        rowCount++;
        reposition();
    }

    public void removePlayer(int playerId) {
        Label playerNameLabel = playerNameLabels[playerId];
        Label playerScoreLabel = playerScoreLabels[playerId];
        Label playerRespawnTimeLabel = playerRespawnTimeLabels[playerId];
        Label playerLatencyLabel = playerLatencyLabels[playerId];
        getCell(playerNameLabel).clearActor();
        getCell(playerScoreLabel).clearActor();
        getCell(playerRespawnTimeLabel).clearActor();
        getCell(playerLatencyLabel).clearActor();
        rowCount--;
        reposition();
    }

    public void setPlayerScore(int playerId, int score, float respawnTime, float latency) {
        playerScoreLabels[playerId].setText(String.valueOf(score));
        playerRespawnTimeLabels[playerId].setText(String.valueOf((int) respawnTime));
        if (latency > 0) {
            Formatter formatter = new Formatter();
            playerLatencyLabels[playerId].setText(formatter.format("%1.2f", latency).toString());
        }
        else {
            playerLatencyLabels[playerId].setText("");
        }
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
        for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
            PlayerInfo playerInfo = refereeSystem.getPlayerInfo(i);
            if (playerInfo.inPlay) {
                setPlayerScore(i, playerInfo.score, playerInfo.respawnTime, playerInfo.latency);
            }
        }
    }
}
