package com.xam.bobgame.components;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.graphics.TextureDef;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class GraphicsComponent implements Component2, Poolable {
    public TextureDef textureDef;
    public Sprite sprite = new Sprite();
    public int z = 0;

    @Override
    public void reset() {
        z = 0;
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        textureDef.type = TextureDef.TextureType.values()[packer.readInt(textureDef.type.getValue(), 0, TextureDef.TextureType.values().length)];
        textureDef.wh = packer.readInt(textureDef.wh, 0, 128);
        textureDef.textureVal1 = packer.readInt(textureDef.textureVal1, 0, 128);
        float r = packer.readFloat(textureDef.color.r, 0, 1, NetDriver.RES_COLOR);
        float g = packer.readFloat(textureDef.color.g, 0, 1, NetDriver.RES_COLOR);
        float b = packer.readFloat(textureDef.color.b, 0, 1, NetDriver.RES_COLOR);
        float a = packer.readFloat(textureDef.color.a, 0, 1, NetDriver.RES_COLOR);
        float w = packer.readFloat(sprite.getWidth(), 0, 16, NetDriver.RES_POSITION);
        float h = packer.readFloat(sprite.getHeight(), 0, 16, NetDriver.RES_POSITION);
        z = packer.readInt(z, 0, GameProperties.Z_POS_MAX);

        if (packer.isReadMode()) {
            textureDef.color.set(r, g, b, a);
            // TODO: don't create texture region if no entity to add to
            sprite.setRegion(new TextureRegion(textureDef.createTexture()));
            sprite.setSize(w, h);
            sprite.setOriginCenter();
        }

        return 0;
    }
}
