package com.xam.bobgame.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pools;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.events.*;
import com.xam.bobgame.events.classes.ConnectionStateRefreshEvent;
import com.xam.bobgame.events.classes.MatchEndedEvent;
import com.xam.bobgame.events.classes.MatchRestartEvent;
import com.xam.bobgame.events.classes.PlayerAssignEvent;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.GameProfile;

public class MainMenu extends Table {

    GameEngine engine;
    RefereeSystem refereeSystem;
    NetDriver netDriver;
    final Skin skin;

    final Cell<?> startMatchCell;
    final Cell<?> joinCell;
    final Cell<?> addressCell;
    final Cell<?> connectCell;
    final Cell<?> serverCell;
    final TextField serverAddressField;
    final TextButton startMatchButton;
    final TextButton newMatchButton;
    final TextButton joinButton;
    final TextButton leaveButton;
    final TextButton connectButton;
    final TextButton disconnectButton;
    final TextButton reconnectButton;
    final TextButton startServerButton;
    final TextButton stopServerButton;

    final Label matchTimeLabel;

    private final ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    public MainMenu(Skin skin) {
        this.skin = skin;

        serverAddressField = new TextField(GameProfile.lastConnectedServerAddress, skin);

        startMatchButton = new TextButton("Start Match", skin);
        startMatchButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                refereeSystem.startMatch();
            }
        });

        newMatchButton = new TextButton("New Match", skin);
        newMatchButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                if (engine.getMode() == GameEngine.Mode.Server) {
                    MatchRestartEvent e = Pools.obtain(MatchRestartEvent.class);
                    engine.getSystem(EventsSystem.class).queueEvent(e);
                }
            }
        });

        joinButton = new TextButton("Join", skin);
        joinButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                refereeSystem.joinGame();
            }
        });

        leaveButton = new TextButton("Leave", skin);
        leaveButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                netDriver.disconnectClient();
            }
        });

        connectButton = new TextButton("Connect", skin);
        connectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                netDriver.connectToServer(serverAddressField.getText());
            }
        });

        disconnectButton = new TextButton("Disconnect", skin);
        disconnectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                netDriver.disconnectClient();
            }
        });

        reconnectButton = new TextButton("Reconnect", skin);
        reconnectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                // TODO: check reconnecting to same address
                netDriver.connectToServer(GameProfile.lastConnectedServerAddress);
            }
        });

        startServerButton = new TextButton("Start Server", skin);
        startServerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                netDriver.startServer();
                engine.start();
            }
        });

        stopServerButton = new TextButton("Stop Server", skin);
        stopServerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                netDriver.stopServer();
            }
        });

        defaults().align(Align.center).fill().expand();
        startMatchCell = add(startMatchButton);
        row();
        joinCell = add(joinButton);
        row();
        addressCell = add(serverAddressField);
        row();
        connectCell = add(connectButton);
        row();
        serverCell = add(startServerButton);
        row();

        add(matchTimeLabel = new Label("0", skin)).align(Align.center);
        matchTimeLabel.setAlignment(Align.center);

        setSize(getPrefWidth(), getPrefHeight());

        listeners.put(PlayerAssignEvent.class, new EventListenerAdapter<PlayerAssignEvent>() {
            @Override
            public void handleEvent(PlayerAssignEvent event) {
                refreshElementStates();
            }
        });
        listeners.put(ConnectionStateRefreshEvent.class, new EventListenerAdapter<ConnectionStateRefreshEvent>() {
            @Override
            public void handleEvent(ConnectionStateRefreshEvent event) {
                refreshElementStates();
            }
        });
        listeners.put(MatchEndedEvent.class, new EventListenerAdapter<MatchEndedEvent>() {
            @Override
            public void handleEvent(MatchEndedEvent event) {
                matchTimeLabel.setText(0);
                refreshElementStates();
            }
        });

        setWidth(350);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (refereeSystem.getMatchState() == RefereeSystem.MatchState.Started) {
            matchTimeLabel.setText((int) refereeSystem.getMatchTimeRemaining());
        }
    }

    void initialize(GameEngine engine) {
        this.engine = engine;
        refereeSystem = engine.getSystem(RefereeSystem.class);
        netDriver = engine.getSystem(NetDriver.class);

        engine.getSystem(EventsSystem.class).addListeners(listeners);
        refreshElementStates();
    }

    void refreshElementStates() {
        // client connected
        if (netDriver.isClientConnecting()) {
            disabled(startMatchCell, startMatchButton);
            disabled(joinCell, joinButton);
            disabled(addressCell, serverAddressField);
            disabled(connectCell, disconnectButton);
            disabled(serverCell, startServerButton);
        }
        else if (netDriver.isClientConnected()) {
            disabled(startMatchCell, startMatchButton);

            if (refereeSystem.isLocalPlayerJoined() || refereeSystem.getMatchState() != RefereeSystem.MatchState.NotStarted) {
                disabled(joinCell, joinButton);
            }
            else {
                enabled(joinCell, joinButton);
            }

            disabled(addressCell, serverAddressField);
            enabled(connectCell, disconnectButton);
            disabled(serverCell, startServerButton);
        }
        // server running
        else if (netDriver.isServerRunning()) {

            switch (refereeSystem.getMatchState()) {
                case NotStarted:
                    enabled(startMatchCell, startMatchButton);
                    if (refereeSystem.isLocalPlayerJoined()) {
                        disabled(joinCell, joinButton);
                    }
                    else {
                        enabled(joinCell, joinButton);
                    }
                    break;
                case Started:
                    disabled(startMatchCell, startMatchButton);
                    disabled(joinCell, joinButton);
                    break;
                case Ended:
                    enabled(startMatchCell, newMatchButton);
                    disabled(joinCell, joinButton);
                    break;
            }

            disabled(addressCell, serverAddressField);
            enabled(serverCell, stopServerButton);
            disabled(connectCell, connectButton);
        }
        else {
            disabled(startMatchCell, startMatchButton);
            if (!netDriver.canReconnect()) {
                disabled(joinCell, joinButton);
                enabled(addressCell, serverAddressField);
                enabled(connectCell, connectButton);
            }
            else {
                enabled(joinCell, leaveButton);
                disabled(addressCell, serverAddressField);
                enabled(connectCell, reconnectButton);
            }
            enabled(serverCell, startServerButton);
        }
    }

    private void enabled(Cell<?> cell, Actor actor) {
        cell.setActor(actor);
        actor.setVisible(true);
        if (actor instanceof Button) ((Button) actor).setDisabled(false);
        if (actor instanceof TextField) ((TextField) actor).setDisabled(false);
        actor.setTouchable(Touchable.enabled);
    }

    private void disabled(Cell<?> cell, Actor actor) {
        cell.setActor(actor);
        actor.setVisible(true);
        if (actor instanceof Button) ((Button) actor).setDisabled(true);
        if (actor instanceof TextField) ((TextField) actor).setDisabled(true);
        actor.setTouchable(Touchable.disabled);
    }

    private void hidden(Cell<?> cell, Actor actor) {
        cell.setActor(actor);
        actor.setVisible(false);
    }
}
