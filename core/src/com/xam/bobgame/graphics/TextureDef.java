package com.xam.bobgame.graphics;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

public class TextureDef {

    public TextureType type = TextureType.Circle;
    public int wh;
    public int textureVal1;
    public Color color = new Color();

    public Texture createTexture() {
        Pixmap pmap = new Pixmap(wh, wh, Pixmap.Format.RGBA8888);
        pmap.setColor(color);
        pmap.fillCircle(wh / 2, wh / 2, textureVal1);
        Texture tx = new Texture(pmap);
        pmap.dispose();
        return tx;
    }

    public enum TextureType {
        Circle(0);

        private int value;

        TextureType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
