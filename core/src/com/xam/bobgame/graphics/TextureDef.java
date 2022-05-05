package com.xam.bobgame.graphics;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

public class TextureDef {

    public TextureType type = TextureType.PlayerBall;
    public int wh;
    public int textureVal1;
    public int textureVal2;
    public Color color = new Color();

    public Texture createTexture() {
        return type.createTexture(this);
    }

    public enum TextureType {
        PlayerBall(0) {
            @Override
             Texture createTexture(TextureDef textureDef) {
                Pixmap pmap = new Pixmap(textureDef.wh, textureDef.wh, Pixmap.Format.RGBA8888);
                pmap.setColor(textureDef.color);
                pmap.fillCircle(textureDef.wh / 2, textureDef.wh / 2, textureDef.textureVal1);
                pmap.setColor(Color.RED);
                pmap.fillRectangle(textureDef.wh / 2 - 1, textureDef.wh / 2, 2, textureDef.wh / 2);
                Texture tx = new Texture(pmap);
                pmap.dispose();
                return tx;
            }
        },
        HazardHole(1) {
            @Override
            Texture createTexture(TextureDef textureDef) {
                Pixmap pmap = new Pixmap(textureDef.wh, textureDef.wh, Pixmap.Format.RGBA8888);
                pmap.setColor(textureDef.color);
                pmap.fillCircle(textureDef.wh / 2, textureDef.wh / 2, textureDef.textureVal1);
                Texture tx = new Texture(pmap);
                pmap.dispose();
                return tx;
            }
        },
        Wall(2) {
            @Override
            Texture createTexture(TextureDef textureDef) {
                Pixmap pmap = new Pixmap(textureDef.wh, textureDef.wh, Pixmap.Format.RGBA8888);
                pmap.setColor(textureDef.color);
                pmap.fillRectangle((textureDef.wh - textureDef.textureVal1) / 2, (textureDef.wh - textureDef.textureVal2) / 2, textureDef.textureVal1, textureDef.textureVal2);
                Texture tx = new Texture(pmap);
                pmap.dispose();
                return tx;
            }
        },
        ;

        private int value;

        TextureType(int value) {
            this.value = value;
        }

        abstract Texture createTexture(TextureDef textureDef);
        public int getValue() {
            return value;
        }
    }
}
