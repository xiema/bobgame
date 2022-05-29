package com.xam.bobgame.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.graphics.TextureDef;
import com.xam.bobgame.graphics.animators.Animated;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.net.NetSerializable;
import com.xam.bobgame.utils.BitPacker;

public class GraphicsComponent implements Component, NetSerializable, Animated, Poolable {
    public TextureDef textureDef;
    public Color baseTint = new Color(Color.WHITE);
    public Sprite sprite = new Sprite();
    public int z = 0;

    public Color drawTint = new Color(Color.WHITE);
    public Vector2 drawOffsets = new Vector2();
    public float drawOrientation = 0;

    @Override
    public void reset() {
        z = 0;
        drawTint.set(baseTint);
        drawOffsets.setZero();
        drawOrientation = 0;
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        textureDef.type = TextureDef.TextureType.values()[packer.readInt(textureDef.type.getValue(), 0, TextureDef.TextureType.values().length)];
        textureDef.wh = packer.readInt(textureDef.wh, 0, 128);
        textureDef.textureVal1 = packer.readInt(textureDef.textureVal1, 0, 128);
        textureDef.textureVal2 = packer.readInt(textureDef.textureVal2, 0, 128);

        float textureDef_r = packer.readFloat(textureDef.color.r, 0, 1, NetDriver.RES_COLOR);
        float textureDef_g = packer.readFloat(textureDef.color.g, 0, 1, NetDriver.RES_COLOR);
        float textureDef_b = packer.readFloat(textureDef.color.b, 0, 1, NetDriver.RES_COLOR);
        float textureDef_a = packer.readFloat(textureDef.color.a, 0, 1, NetDriver.RES_COLOR);

        float baseTint_r = packer.readFloat(baseTint.r, 0, 1, NetDriver.RES_COLOR);
        float baseTint_g = packer.readFloat(baseTint.g, 0, 1, NetDriver.RES_COLOR);
        float baseTint_b = packer.readFloat(baseTint.b, 0, 1, NetDriver.RES_COLOR);
        float baseTint_a = packer.readFloat(baseTint.a, 0, 1, NetDriver.RES_COLOR);

        float w = packer.readFloat(sprite.getWidth(), 0, 16, NetDriver.RES_POSITION);
        float h = packer.readFloat(sprite.getHeight(), 0, 16, NetDriver.RES_POSITION);
        z = packer.readInt(z, 0, GameProperties.Z_POS_MAX);

        if (packer.isReadMode()) {
            textureDef.color.set(textureDef_r, textureDef_g, textureDef_b, textureDef_a);
            baseTint.set(baseTint_r, baseTint_g, baseTint_b, baseTint_a);
            // TODO: don't create texture region if no entity to add to
            sprite.setRegion(new TextureRegion(textureDef.createTexture()));
            sprite.setSize(w, h);
            sprite.setOriginCenter();
        }

        return 0;
    }

    @Override
    public void setDrawTint(Color color) {
        drawTint.set(color);
    }

    @Override
    public void setDrawTint(float r, float g, float b, float a) {
        drawTint.set(r, g, b, a);
    }

    @Override
    public void setAlpha(float a) {
        drawTint.set(drawTint.r, drawTint.g, drawTint.b, a);
    }

    @Override
    public void setDrawOffsets(float x, float y, float orientation) {
        drawOffsets.set(x, y);
        drawOrientation = orientation;
    }

    @Override
    public Color getDrawTint() {
        return drawTint;
    }

    @Override
    public float getDrawX() {
        return drawOffsets.x;
    }

    @Override
    public float getDrawY() {
        return drawOffsets.y;
    }

    @Override
    public float getX() {
        return sprite.getX();
    }

    @Override
    public float getY() {
        return sprite.getY();
    }

    @Override
    public float getOriginX() {
        return sprite.getOriginX();
    }

    @Override
    public float getOriginY() {
        return sprite.getOriginY();
    }

    @Override
    public float getRotation() {
        return sprite.getRotation();
    }

    @Override
    public float getDrawOrientation() {
        return drawOrientation;
    }
}
