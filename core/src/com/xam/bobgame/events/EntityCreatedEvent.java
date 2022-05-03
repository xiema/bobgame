package com.xam.bobgame.events;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameDirector;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.components.IdentityComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityType;
import com.xam.bobgame.game.ShapeDef;
import com.xam.bobgame.graphics.TextureDef;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class EntityCreatedEvent extends NetDriver.NetworkEvent {
    public int entityId = -1;

    @Override
    public void reset() {
        super.reset();
        entityId = -1;
    }

    @Override
    public void read(BitPacker builder, Engine engine, boolean write) {
        entityId = readInt(builder, entityId, 0, 255, write);
        
        Entity entity;
        PhysicsBodyComponent pb;
        GraphicsComponent graphics;
        IdentityComponent iden;

        if (!write) {
            entity = engine.createEntity();
            pb = engine.createComponent(PhysicsBodyComponent.class);
            pb.bodyDef = new BodyDef();
            pb.fixtureDef = new FixtureDef();
            pb.shapeDef = new ShapeDef();
            graphics = engine.createComponent(GraphicsComponent.class);
            graphics.textureDef = new TextureDef();
            iden = engine.createComponent(IdentityComponent.class);
        }
        else {
            entity = engine.getSystem(GameDirector.class).getEntityById(entityId);
            pb = ComponentMappers.physicsBody.get(entity);
            graphics = ComponentMappers.graphics.get(entity);
            iden = ComponentMappers.identity.get(entity);
        }

        iden.id = readInt(builder, iden.id, 0, 255, write);
        iden.type = EntityType.values()[readInt(builder, iden.type.getValue(), 0, EntityType.values().length, write)];

        pb.bodyDef.type = BodyDef.BodyType.values()[readInt(builder, pb.bodyDef.type.getValue(), 0, BodyDef.BodyType.values().length, write)];
        pb.bodyDef.position.x = readFloat(builder, pb.bodyDef.position.x, -3, GameProperties.MAP_WIDTH + 3, NetDriver.RES_POSITION, write);
        pb.bodyDef.position.y = readFloat(builder, pb.bodyDef.position.y, -3, GameProperties.MAP_HEIGHT + 3, NetDriver.RES_POSITION, write);
        pb.bodyDef.linearDamping = readFloat(builder, pb.bodyDef.linearDamping, 0, 1, NetDriver.RES_MASS, write);
        pb.shapeDef.type = ShapeDef.ShapeType.values()[readInt(builder, pb.shapeDef.type.getValue(), 0, ShapeDef.ShapeType.values().length, write)];
        pb.shapeDef.shapeVal1 = readFloat(builder, pb.shapeDef.shapeVal1, 0, 16, NetDriver.RES_POSITION, write);
        pb.fixtureDef.density = readFloat(builder, pb.fixtureDef.density, 0, 16, NetDriver.RES_MASS, write);
        pb.fixtureDef.friction = readFloat(builder, pb.fixtureDef.friction, 0, 16, NetDriver.RES_MASS, write);
        pb.fixtureDef.restitution = readFloat(builder, pb.fixtureDef.restitution, 0, 16, NetDriver.RES_MASS, write);

        graphics.textureDef.type = TextureDef.TextureType.values()[readInt(builder, graphics.textureDef.type.getValue(), 0, TextureDef.TextureType.values().length, write)];
        graphics.textureDef.wh = readInt(builder, graphics.textureDef.wh, 0, 128, write);
        graphics.textureDef.textureVal1 = readInt(builder, graphics.textureDef.textureVal1, 0, 128, write);
        float r = readFloat(builder, graphics.textureDef.color.r, 0, 1, NetDriver.RES_COLOR, write);
        float g = readFloat(builder, graphics.textureDef.color.g, 0, 1, NetDriver.RES_COLOR, write);
        float b = readFloat(builder, graphics.textureDef.color.b, 0, 1, NetDriver.RES_COLOR, write);
        float a = readFloat(builder, graphics.textureDef.color.a, 0, 1, NetDriver.RES_COLOR, write);
        float w = readFloat(builder, graphics.spriteActor.getSprite().getWidth(), 0, 16, NetDriver.RES_POSITION, write);
        float h = readFloat(builder, graphics.spriteActor.getSprite().getHeight(), 0, 16, NetDriver.RES_POSITION, write);
        graphics.z = readInt(builder, graphics.z, 0, GameProperties.Z_POS_MAX, write);

        if (!write) {
            graphics.textureDef.color.set(r, g, b, a);
            Sprite sprite = graphics.spriteActor.getSprite();
            sprite.setRegion(new TextureRegion(graphics.textureDef.createTexture()));
            sprite.setSize(w, h);
            sprite.setOriginCenter();

            pb.fixtureDef.friction *= NetDriver.FRICTION_FACTOR;
            pb.fixtureDef.restitution *= NetDriver.RESTITUTION_FACTOR;
            pb.bodyDef.linearDamping *= NetDriver.DAMPING_FACTOR;

            entity.add(iden);
            entity.add(pb);
            entity.add(graphics);

            engine.addEntity(entity);
        }
    }
}
