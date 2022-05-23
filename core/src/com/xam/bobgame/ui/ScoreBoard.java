package com.xam.bobgame.ui;

import com.badlogic.ashley.utils.ImmutableArray;
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
import com.xam.bobgame.events.classes.*;
import com.xam.bobgame.game.PlayerInfo;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.net.NetDriver;

import java.util.Formatter;

public class ScoreBoard extends Table {

    GameEngine engine;
    final Skin skin;

    private final Cell<?>[] playerNameCells = new Cell[GameProperties.MAX_PLAYERS];
    private final Cell<?>[] playerScoreCells = new Cell[GameProperties.MAX_PLAYERS];
    private final Cell<?>[] playerRespawnTimeCells = new Cell[GameProperties.MAX_PLAYERS];
    private final Cell<?>[] playerLatencyCells = new Cell[GameProperties.MAX_PLAYERS];
    private final Label[] playerNameLabels = new Label[NetDriver.MAX_CLIENTS];
    private final Label[] playerScoreLabels = new Label[NetDriver.MAX_CLIENTS];
    private final Label[] playerRespawnTimeLabels = new Label[NetDriver.MAX_CLIENTS];
    private final Label[] playerLatencyLabels = new Label[NetDriver.MAX_CLIENTS];

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    private int rowCount = 0;

    public ScoreBoard(Skin skin) {
        this.skin = skin;

        setBackground(skin.getDrawable("blue"));

        align(Align.top);
        padTop(5);
        columnDefaults(0).align(Align.center).expandX();
        columnDefaults(1).align(Align.center).width(90).spaceLeft(15);
        columnDefaults(2).align(Align.center).width(90);
        columnDefaults(3).align(Align.center).width(110);

        defaults().padBottom(10);
        Label label;
        add(label = new Label("Name", skin, "defaultWhite"));
        label.setAlignment(Align.center);
        add(label = new Label("Score", skin, "defaultWhite"));
        label.setAlignment(Align.center);
        add(label = new Label("Spawn", skin, "defaultWhite"));
        label.setAlignment(Align.center);
        add(label = new Label("Ping", skin, "defaultWhite"));
        label.setAlignment(Align.center);
        row();

        defaults().padBottom(1);
        for (int i = 0; i < playerNameLabels.length; ++i) {
            playerNameLabels[i] = new Label("-", skin);
            playerScoreLabels[i] = new Label("-", skin);
            playerRespawnTimeLabels[i] = new Label("-", skin);
            playerLatencyLabels[i] = new Label("-", skin);

            playerNameLabels[i].setAlignment(Align.center);
            playerRespawnTimeLabels[i].setAlignment(Align.center);
            playerScoreLabels[i].setAlignment(Align.center);
            playerLatencyLabels[i].setAlignment(Align.center);

            if (i < playerNameCells.length) {
                playerNameCells[i] = add(playerNameLabels[i]);
                playerScoreCells[i] = add(playerScoreLabels[i]);
                playerRespawnTimeCells[i] = add(playerRespawnTimeLabels[i]);
                playerLatencyCells[i] = add(playerLatencyLabels[i]);
            }

            row();
        }

        listeners.put(PlayerJoinedEvent.class, new EventListenerAdapter<PlayerJoinedEvent>() {
            @Override
            public void handleEvent(PlayerJoinedEvent event) {
                addPlayer(event.playerId);
//                refreshScoreBoard();
            }
        });
        listeners.put(ClientConnectedEvent.class, new EventListenerAdapter<ClientConnectedEvent>() {
            @Override
            public void handleEvent(ClientConnectedEvent event) {
                refreshScoreBoard();
            }
        });
        listeners.put(PlayerLeftEvent.class, new EventListenerAdapter<PlayerLeftEvent>() {
            @Override
            public void handleEvent(PlayerLeftEvent event) {
//                removePlayer(event.playerId);
                refreshScoreBoard();
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
        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
        for (int i = 0; i < playerNameLabels.length; ++i) {
            setPlayerStyle(i, i == refereeSystem.getLocalPlayerId() ? "defaultWhite" : "default");
        }
    }

    private void reposition() {
//        setSize(GameProperties.MENU_WIDTH, );
//        setPosition(GameProperties.WINDOW_WIDTH, GameProperties.WINDOW_HEIGHT, Align.topRight);
    }

    private void setPlayerStyle(int playerId, String styleName) {
        if (!skin.has(styleName, Label.LabelStyle.class)) {
            Log.error("Skin has no LabelStyle named " + styleName);
            return;
        }
        Label.LabelStyle style = skin.get(styleName, Label.LabelStyle.class);
        if (playerNameLabels[playerId].getStyle() != style) playerNameLabels[playerId].setStyle(style);
        if (playerScoreLabels[playerId].getStyle() != style) playerScoreLabels[playerId].setStyle(style);
        if (playerRespawnTimeLabels[playerId].getStyle() != style) playerRespawnTimeLabels[playerId].setStyle(style);
        if (playerLatencyLabels[playerId].getStyle() != style) playerLatencyLabels[playerId].setStyle(style);
    }

    private void refreshScoreBoard() {
        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
        if (refereeSystem == null) return;

        for (int i = 0; i < playerNameCells.length; ++i) {
            playerNameCells[i].clearActor();
            playerScoreCells[i].clearActor();
            playerRespawnTimeCells[i].clearActor();
            playerLatencyCells[i].clearActor();
        }

        ImmutableArray<PlayerInfo> playerInfos = refereeSystem.getSortedPlayerInfos();
        int i = 0;
        for (PlayerInfo playerInfo : playerInfos) {
            playerNameCells[i].setActor(playerNameLabels[playerInfo.playerId]);
            playerScoreCells[i].setActor(playerScoreLabels[playerInfo.playerId]);
            playerRespawnTimeCells[i].setActor(playerRespawnTimeLabels[playerInfo.playerId]);
            playerLatencyCells[i].setActor(playerLatencyLabels[playerInfo.playerId]);
            setPlayerStyle(playerInfo.playerId, playerInfo.playerId == refereeSystem.getLocalPlayerId() ? "defaultWhite" : "default");
            setPlayerScore("PL." + playerInfo.playerId, playerInfo.playerId, playerInfo.score, playerInfo.respawnTime, playerInfo.latency);
            i++;
        }

        reposition();
    }

    public void addPlayer(int playerId) {
        PlayerInfo playerInfo = engine.getSystem(RefereeSystem.class).getPlayerInfo(playerId);
        playerNameLabels[playerId].setText("PL." + playerInfo.playerId);
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
//        playerNameLabels[playerId].setText("-");
//        playerScoreLabels[playerId].setText("-");
//        playerRespawnTimeLabels[playerId].setText("-");
//        playerLatencyLabels[playerId].setText("-");
    }

    public void setPlayerScore(String playerName, int playerId, int score, float respawnTime, float latency) {
        playerNameLabels[playerId].setText(playerName);
        playerScoreLabels[playerId].setText(String.valueOf(score));
        playerRespawnTimeLabels[playerId].setText(String.valueOf((int) respawnTime));
        if (latency > 0) {
            Formatter formatter = new Formatter();
            playerLatencyLabels[playerId].setText(formatter.format("%1.2f", latency).toString());
        }
        else {
            playerLatencyLabels[playerId].setText("-");
        }
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
        for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
            PlayerInfo playerInfo = refereeSystem.getPlayerInfo(i);
            if (playerInfo.inPlay) {
                setPlayerScore("PL." + playerInfo.playerId, playerInfo.playerId, playerInfo.score, playerInfo.respawnTime, playerInfo.latency);
            }
        }
    }
}
