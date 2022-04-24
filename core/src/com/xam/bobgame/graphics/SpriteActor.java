package com.xam.bobgame.graphics;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.components.PositionComponent;
import com.xam.bobgame.entity.ComponentMappers;

public class SpriteActor extends Actor {
    Entity entity;

    public SpriteActor(Entity entity) {
        this.entity = entity;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        PositionComponent pos = ComponentMappers.position.get(entity);
        batch.draw(ComponentMappers.graphics.get(entity).txtReg, pos.vec.x, pos.vec.y);
    }
}
