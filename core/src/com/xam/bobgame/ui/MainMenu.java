package com.xam.bobgame.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Action;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pools;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.events.*;
import com.xam.bobgame.events.classes.*;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.GameProfile;

import java.util.Formatter;

@SuppressWarnings("rawtypes")
public class MainMenu extends Table {

    GameEngine engine;
    RefereeSystem refereeSystem;
    NetDriver netDriver;
    final Skin skin;

    final Array<Cell> cells = getCells();
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

    final Label sendBitrateLabel;
    final Label receiveBitrateLabel;

    private final ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    public MainMenu(Skin skin) {
        this.skin = skin;

        defaults().expand().fill();
        columnDefaults(0).width(0.5f * GameProperties.MENU_WIDTH);
        columnDefaults(1).width(0.5f * GameProperties.MENU_WIDTH);

        for (int i = 0; i < 5; ++i) {
            for (int j = 0; j < 2; ++j) {
                add();
            }
            row();
        }

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
                ClientConnectedEvent e = Pools.obtain(ClientConnectedEvent.class);
                e.clientId = -1;
                engine.getSystem(EventsSystem.class).queueEvent(e);
            }
        });

        stopServerButton = new TextButton("Stop Server", skin);
        stopServerButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                netDriver.stopServer();
            }
        });

        FieldValuePair pair;
        pair = new FieldValuePair("Recv", "0", skin);
        receiveBitrateLabel = pair.value;
        receiveBitrateLabel.addAction(Actions.forever(new Action() {
            @Override
            public boolean act(float delta) {
                Formatter formatter = new Formatter();
                receiveBitrateLabel.setText(formatter.format("%1.2f", netDriver.getAverageReceiveBitrate()).toString());
                return false;
            }
        }));
        cells.get(8).setActor(pair);
        pair = new FieldValuePair("Send", "0", skin);
        sendBitrateLabel = pair.value;
        sendBitrateLabel.addAction(Actions.forever(new Action() {
            @Override
            public boolean act(float delta) {
                Formatter formatter = new Formatter();
                sendBitrateLabel.setText(formatter.format("%1.2f", netDriver.getAverageSendBitrate()).toString());
                return false;
            }
        }));
        sendBitrateLabel.setAlignment(Align.right);
        receiveBitrateLabel.setAlignment(Align.right);
        cells.get(9).setActor(pair);

        defaults().align(Align.center).fill().expand();

//        setSize(getPrefWidth(), getPrefHeight());

        listeners.put(PlayerAssignEvent.class, new EventListenerAdapter<PlayerAssignEvent>() {
            @Override
            public void handleEvent(PlayerAssignEvent event) {
                refreshElementStates();
            }
        });
        listeners.put(ClientConnectedEvent.class, new EventListenerAdapter<ClientConnectedEvent>() {
            @Override
            public void handleEvent(ClientConnectedEvent event) {
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
                refreshElementStates();
            }
        });
    }

    @Override
    public void act(float delta) {
        super.act(delta);
    }

    void initialize(GameEngine engine) {
        this.engine = engine;
        refereeSystem = engine.getSystem(RefereeSystem.class);
        netDriver = engine.getSystem(NetDriver.class);

        engine.getSystem(EventsSystem.class).addListeners(listeners);
        refreshElementStates();
    }

    void refreshElementStates() {
        // client
        if (netDriver.isClientConnecting()) {
            disabled(cells.get(0), startServerButton);
            disabled(cells.get(2), disconnectButton);
            disabled(cells.get(3), serverAddressField);
            disabled(cells.get(4), startMatchButton);
            disabled(cells.get(6), joinButton);
        }
        else if (netDriver.isClientConnected()) {
            disabled(cells.get(0), startServerButton);
            enabled(cells.get(2), disconnectButton);
            disabled(cells.get(3), serverAddressField);
            disabled(cells.get(4), startMatchButton);

            if (refereeSystem.isLocalPlayerJoined() || refereeSystem.getMatchState() != RefereeSystem.MatchState.NotStarted) {
                disabled(cells.get(6), joinButton);
            }
            else {
                enabled(cells.get(6), joinButton);
            }

        }
        // server
        else if (netDriver.isServerRunning()) {
            enabled(cells.get(0), stopServerButton);
            disabled(cells.get(2), connectButton);
            disabled(cells.get(3), serverAddressField);

            switch (refereeSystem.getMatchState()) {
                case NotStarted:
                    if (refereeSystem.getPlayerCount() > 0) {
                        enabled(cells.get(4), startMatchButton);
                    }
                    else {
                        disabled(cells.get(4), startMatchButton);
                    }
                    if (refereeSystem.isLocalPlayerJoined()) {
                        disabled(cells.get(6), joinButton);
                    }
                    else {
                        enabled(cells.get(6), joinButton);
                    }
                    break;
                case Started:
                    disabled(cells.get(4), startMatchButton);
                    disabled(cells.get(6), joinButton);
                    break;
                case Ended:
                    enabled(cells.get(4), newMatchButton);
                    disabled(cells.get(6), joinButton);
                    break;
            }

        }
        else {
            enabled(cells.get(0), startServerButton);
            if (!netDriver.canReconnect()) {
                enabled(cells.get(2), connectButton);
                enabled(cells.get(3), serverAddressField);
                disabled(cells.get(4), startMatchButton);
                disabled(cells.get(6), joinButton);
            }
            else {
                enabled(cells.get(2), reconnectButton);
                disabled(cells.get(3), serverAddressField);
                disabled(cells.get(4), startMatchButton);
                enabled(cells.get(6), leaveButton);
            }
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

    private class FieldValuePair extends Table {
        final Label field;
        final Label value;

        public FieldValuePair(String fieldText, String valueText, Skin skin) {
            setBackground(skin.getDrawable("white"));
            setColor(Color.BLACK);
            columnDefaults(0).width(90);
            columnDefaults(1).expandX().fillX();
            add(field = new Label(fieldText, skin)).padLeft(10);
            add(value = new Label(valueText, skin)).padRight(10);
        }
    }
}
