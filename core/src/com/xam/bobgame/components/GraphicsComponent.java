package com.xam.bobgame.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.graphics.SpriteActor;

public class GraphicsComponent implements Component, Poolable {
    public SpriteActor spriteActor = new SpriteActor();

    @Override
    public void reset() {

    }
}
