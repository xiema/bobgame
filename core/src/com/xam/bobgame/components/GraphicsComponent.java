package com.xam.bobgame.components;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.graphics.SpriteActor;
import com.xam.bobgame.graphics.TextureDef;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class GraphicsComponent extends Component2 implements Poolable {
    public TextureDef textureDef;
    public SpriteActor spriteActor = new SpriteActor();
    public int z = 0;

    @Override
    public void reset() {
        z = 0;
    }

    @Override
    public void read(BitPacker packer, Engine engine, boolean write) {
        textureDef.type = TextureDef.TextureType.values()[readInt(packer, textureDef.type.getValue(), 0, TextureDef.TextureType.values().length, write)];
        textureDef.wh = readInt(packer, textureDef.wh, 0, 128, write);
        textureDef.textureVal1 = readInt(packer, textureDef.textureVal1, 0, 128, write);
        float r = readFloat(packer, textureDef.color.r, 0, 1, NetDriver.RES_COLOR, write);
        float g = readFloat(packer, textureDef.color.g, 0, 1, NetDriver.RES_COLOR, write);
        float b = readFloat(packer, textureDef.color.b, 0, 1, NetDriver.RES_COLOR, write);
        float a = readFloat(packer, textureDef.color.a, 0, 1, NetDriver.RES_COLOR, write);
        float w = readFloat(packer, spriteActor.getSprite().getWidth(), 0, 16, NetDriver.RES_POSITION, write);
        float h = readFloat(packer, spriteActor.getSprite().getHeight(), 0, 16, NetDriver.RES_POSITION, write);
        z = readInt(packer, z, 0, GameProperties.Z_POS_MAX, write);

        if (!write) {
            textureDef.color.set(r, g, b, a);
            Sprite sprite = spriteActor.getSprite();
            // TODO: don't create texture region if no entity to add to
            sprite.setRegion(new TextureRegion(textureDef.createTexture()));
            sprite.setSize(w, h);
            sprite.setOriginCenter();
        }
    }
}
