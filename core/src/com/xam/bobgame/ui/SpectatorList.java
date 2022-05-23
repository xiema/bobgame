package com.xam.bobgame.ui;

import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.Align;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.net.NetDriver;

public class SpectatorList extends Table {

    private GameEngine engine;
    private NetDriver netDriver;
    private final Label[] labels = new Label[33];

    public SpectatorList(Skin skin) {
        setBackground(skin.getDrawable("blue"));

        defaults().align(Align.center).expandX().fillX();

        Label label;

        padTop(5);
        add(label = new Label("Spectators", skin)).colspan(3).padBottom(10);
        label.setAlignment(Align.center);
        row();

        for (int i = 0; i < 11; ++i) {
            for (int j = 0; j < 3; ++j) {
                add(labels[i * 3 + j] = label = new Label("-", skin));
                label.setAlignment(Align.center);
            }
            row();
        }
    }

    public void initialize(GameEngine engine) {
        this.engine = engine;
        netDriver = engine.getSystem(NetDriver.class);
    }
}
