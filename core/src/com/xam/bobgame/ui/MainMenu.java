package com.xam.bobgame.ui;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectMap;
import com.xam.bobgame.BoBGame;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.events.*;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.GameProfile;

public class MainMenu extends Table {

    GameEngine engine;
    RefereeSystem refereeSystem;
    NetDriver netDriver;
    final Skin skin;

    final Cell<?> joinCell;
    final Cell<?> connectCell;
    final Cell<?> serverCell;
    final Cell<?> addressCell;
    final TextField serverAddressField;
    final TextButton startGameButton;
    final TextButton joinButton;
    final TextButton connectButton;
    final TextButton disconnectButton;
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
                refereeSystem.joinPlayer(-1);
                refreshElementStates();
            }
        });

        joinButton = new TextButton("Join", skin);
        joinButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                refereeSystem.joinGame();
                refreshElementStates();
            }
        });

        connectButton = new TextButton("Connect", skin);
        connectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                netDriver.connectToServer(serverAddressField.getText());
                refreshElementStates();
            }
        });

        disconnectButton = new TextButton("Disconnect", skin);
        disconnectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                netDriver.getClient().disconnect();
                refreshElementStates();
            }
        });

        startServerButton = new TextButton("Start Server", skin);
        startServerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                netDriver.startServer();
                refreshElementStates();
            }
        });

        stopServerButton = new TextButton("Stop Server", skin);
        stopServerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                netDriver.getServer().stop();
                refreshElementStates();
            }
        });

        defaults().align(Align.center).fill().expand();
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
        if (netDriver.isClientConnected()) {
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
                disabled(joinCell, startGameButton);
            }
            else {
                enabled(joinCell, startGameButton);
            }

            disabled(addressCell, serverAddressField);
            enabled(serverCell, stopServerButton);
            disabled(connectCell, connectButton);
        }
        else {
            disabled(joinCell, joinButton);
            enabled(addressCell, serverAddressField);
            enabled(connectCell, connectButton);
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
