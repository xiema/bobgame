package com.xam.bobgame.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectMap;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.events.*;
import com.xam.bobgame.events.classes.ConnectionStateRefreshEvent;
import com.xam.bobgame.events.classes.PlayerAssignEvent;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.GameProfile;

public class MainMenu extends Table {

    GameEngine engine;
    RefereeSystem refereeSystem;
    NetDriver netDriver;
    final Skin skin;

    final Cell<?> startGameCell;
    final Cell<?> joinCell;
    final Cell<?> addressCell;
    final Cell<?> connectCell;
    final Cell<?> serverCell;
    final TextField serverAddressField;
    final TextButton startGameButton;
    final TextButton joinButton;
    final TextButton leaveButton;
    final TextButton connectButton;
    final TextButton disconnectButton;
    final TextButton reconnectButton;
    final TextButton startServerButton;
    final TextButton stopServerButton;

    private final ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    public MainMenu(Skin skin) {
        this.skin = skin;

        serverAddressField = new TextField(GameProfile.lastConnectedServerAddress, skin);

        startGameButton = new TextButton("Start Game", skin);
        startGameButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                engine.start();
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
        startGameCell = add(startGameButton);
        row();
        joinCell = add(joinButton);
        row();
        addressCell = add(serverAddressField);
        row();
        connectCell = add(connectButton);
        row();
        serverCell = add(startServerButton);
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

        setWidth(350);
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
            disabled(startGameCell, startGameButton);
            disabled(joinCell, joinButton);
            disabled(addressCell, serverAddressField);
            disabled(connectCell, disconnectButton);
            disabled(serverCell, startServerButton);
        }
        else if (netDriver.isClientConnected()) {
            disabled(startGameCell, startGameButton);

            if (refereeSystem.isLocalPlayerJoined()) {
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

            if (refereeSystem.isMatchStarted()) {
                disabled(startGameCell, startGameButton);
                if (refereeSystem.isLocalPlayerJoined()) {
                    disabled(joinCell, joinButton);
                }
                else {
                    enabled(joinCell, joinButton);
                }
            }
            else {
                enabled(startGameCell, startGameButton);
                disabled(joinCell, joinButton);
            }

            disabled(addressCell, serverAddressField);
            enabled(serverCell, stopServerButton);
            disabled(connectCell, connectButton);
        }
        else {
            disabled(startGameCell, startGameButton);
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
