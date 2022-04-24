package com.xam.bobgame.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Pool.Poolable;

public class GraphicsComponent implements Component, Poolable {
    public TextureRegion txtReg;

    @Override
    public void reset() {

    }
}
