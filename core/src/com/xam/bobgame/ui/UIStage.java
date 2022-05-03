package com.xam.bobgame.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.BoBGame;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.events.*;
import com.xam.bobgame.net.NetDriver;

public class UIStage extends Stage {

    final BoBGame game;
    final NetDriver netDriver;

    final Label bitrateLabel;
    final TextField serverAddressField;
    final TextButton connectButton;
    final TextButton disconnectButton;

    final ForceMeter forceMeter;

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    public UIStage(final BoBGame game, Viewport viewport, Batch batch, Skin skin) {
        super(viewport, batch);
        this.game = game;
        netDriver = game.getEngine().getSystem(NetDriver.class);

        bitrateLabel = new Label("0", skin) {
            @Override
            public void act(float delta) {
                super.act(delta);
                setText(String.valueOf(netDriver.getAverageBitrate()));
            }
        };
//        bitrateLabel.setName("bitrateLabel");
        bitrateLabel.setPosition(0, 0, Align.bottomLeft);
        addActor(bitrateLabel);

        serverAddressField = new TextField("127.0.0.1", skin);
        serverAddressField.setPosition(0, 500, Align.topLeft);
        serverAddressField.setWidth(250);
        addActor(serverAddressField);

        connectButton = new TextButton("Connect", skin);
        connectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                netDriver.getClient().connect(serverAddressField.getText());
            }
        });
        connectButton.setPosition(0, 450, Align.topLeft);
        addActor(connectButton);

        disconnectButton = new TextButton("Disconnect", skin);
        disconnectButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                netDriver.getClient().disconnect();
            }
        });
        disconnectButton.setPosition(0, 400, Align.topLeft);
        addActor(disconnectButton);

        forceMeter = new ForceMeter(skin);
        forceMeter.setPosition(800, 0, Align.bottomRight);
        addActor(forceMeter);

        ((InputMultiplexer) Gdx.input.getInputProcessor()).addProcessor(this);
    }

    public void initialize(GameEngine engine) {
        forceMeter.initialize(engine);
    }

    @Override
    public void draw() {
        super.draw();
    }
}
