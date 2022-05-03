package com.xam.bobgame.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.graphics.SpriteActor;
import com.xam.bobgame.graphics.TextureDef;

public class GraphicsComponent implements Component, Poolable {
    public TextureDef textureDef;
    public SpriteActor spriteActor = new SpriteActor();
    public int z = 0;

    @Override
    public void reset() {
        z = 0;
    }
}
