package com.xam.bobgame.graphics;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

public class TextureDef {

    // TODO: Create all textures on application start
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
        Star(3) {
            @Override
            Texture createTexture(TextureDef textureDef) {
                Pixmap pmap = new Pixmap(textureDef.wh, textureDef.wh, Pixmap.Format.RGBA8888);
                pmap.setColor(textureDef.color);
                int cx = textureDef.wh / 2, cy = textureDef.wh / 2;
                int x1 = cx - textureDef.textureVal2, x2 = cx + textureDef.textureVal2;
                int y1 = cy + textureDef.textureVal2, y2 = cy - textureDef.textureVal2;
                pmap.fillRectangle(x1, y1, textureDef.textureVal2, textureDef.textureVal2);
                pmap.fillTriangle(x1, y1, 0, cy, x1, y2);
                pmap.fillTriangle(x1, y2, cx, textureDef.textureVal1 * 2, x2, y2);
                pmap.fillTriangle(x2, y2, textureDef.textureVal1 * 2, cy, x2, y1);
                pmap.fillTriangle(x2, y1, cx, 0, x1, y1);

                pmap.setColor(Color.RED);
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
