package com.xam.bobgame.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectMap;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.events.EventListenerAdapter;
import com.xam.bobgame.events.EventsSystem;
import com.xam.bobgame.events.GameEvent;
import com.xam.bobgame.events.GameEventListener;
import com.xam.bobgame.events.classes.*;
import com.xam.bobgame.game.PlayerInfo;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.net.NetDriver;

public class SpectatorList extends Table {

    final Skin skin;

    private GameEngine engine;
    private NetDriver netDriver;
    private final Label[] labels = new Label[33];
    private final Cell<?>[] cells = new Cell<?>[33];

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    public SpectatorList(Skin skin) {
        this.skin = skin;

        setBackground(skin.getDrawable("blue"));

        align(Align.top);
        defaults().align(Align.center).expandX().fillX();

        Label label;

        padTop(5);
        add(label = new Label("Spectators", skin, "defaultWhite")).colspan(3).padBottom(10);
        label.setAlignment(Align.center);
        row();

        for (int i = 0; i < 11; ++i) {
            for (int j = 0; j < 3; ++j) {
                cells[i * 3 + j] = add(labels[i * 3 + j] = label = new Label("-", skin));
                label.setAlignment(Align.center);
            }
            row();
        }

        listeners.put(ScoreBoardRefreshEvent.class, new EventListenerAdapter<ScoreBoardRefreshEvent>() {
            @Override
            public void handleEvent(ScoreBoardRefreshEvent event) {
                refresh();
            }
        });
        listeners.put(PlayerJoinedEvent.class, new EventListenerAdapter<PlayerJoinedEvent>() {
            @Override
            public void handleEvent(PlayerJoinedEvent event) {
                refresh();
            }
        });
        listeners.put(PlayerLeftEvent.class, new EventListenerAdapter<PlayerLeftEvent>() {
            @Override
            public void handleEvent(PlayerLeftEvent event) {
                refresh();
            }
        });
        listeners.put(PlayerAssignEvent.class, new EventListenerAdapter<PlayerAssignEvent>() {
            @Override
            public void handleEvent(PlayerAssignEvent event) {
                refresh();
            }
        });
        listeners.put(ClientConnectedEvent.class, new EventListenerAdapter<ClientConnectedEvent>() {
            @Override
            public void handleEvent(ClientConnectedEvent event) {
                refresh();
            }
        });
    }

    private void refresh() {
        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
        if (refereeSystem == null) return;

        for (Cell<?> cell : cells) cell.clearActor();

        int j = 0;
        for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
            PlayerInfo playerInfo = refereeSystem.getPlayerInfo(i);
            if (playerInfo.connected && !playerInfo.inPlay) {
                labels[i].setText("PL." + playerInfo.playerId);
                if (i == refereeSystem.getLocalPlayerId()) setPlayerStyle(i, "defaultWhite");
                cells[j++].setActor(labels[i]);
            }
        }
    }

    private void setPlayerStyle(int playerId, String styleName) {
        if (!skin.has(styleName, Label.LabelStyle.class)) {
            Log.error("Skin has no LabelStyle named " + styleName);
            return;
        }
        Label.LabelStyle style = skin.get(styleName, Label.LabelStyle.class);
        if (labels[playerId].getStyle() != style) labels[playerId].setStyle(style);
    }

    public void initialize(GameEngine engine) {
        this.engine = engine;
        netDriver = engine.getSystem(NetDriver.class);
        engine.getSystem(EventsSystem.class).addListeners(listeners);
        refresh();
    }
}
