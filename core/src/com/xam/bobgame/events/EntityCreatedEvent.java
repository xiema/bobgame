package com.xam.bobgame.events;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.FixtureDef;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.components.*;
import com.xam.bobgame.entity.ComponentMappers;
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
    public NetDriver.NetworkEvent copyTo(NetDriver.NetworkEvent event) {
        EntityCreatedEvent other = (EntityCreatedEvent) event;
        other.entityId = entityId;
        return super.copyTo(event);
    }

    @Override
    public void read(BitPacker packer, Engine engine, boolean send) {
        entityId = readInt(packer, entityId, 0, NetDriver.MAX_ENTITY_ID, send);
        
        Entity entity = ((GameEngine) engine).getEntityById(entityId);

        IdentityComponent iden;
        PhysicsBodyComponent pb;
        GraphicsComponent graphics;
        HazardComponent hazard;
        GravitationalFieldComponent gravField;

        if (send) {
            iden = ComponentMappers.identity.get(entity);
            pb = ComponentMappers.physicsBody.get(entity);
            graphics = ComponentMappers.graphics.get(entity);
            hazard = ComponentMappers.hazards.get(entity);
            gravField = ComponentMappers.gravFields.get(entity);

            readBoolean(packer, pb != null, send);
            readBoolean(packer, graphics != null, send);
            readBoolean(packer, hazard != null, send);
            readBoolean(packer, gravField != null, send);
        }
        else {
            if (entity == null) {
                iden = engine.createComponent(IdentityComponent.class);
                pb = readBoolean(packer, true, send) ? engine.createComponent(PhysicsBodyComponent.class) : null;
                graphics = readBoolean(packer, true, send) ? engine.createComponent(GraphicsComponent.class) : null;
                hazard = readBoolean(packer, true, send) ? engine.createComponent(HazardComponent.class) : null;
                gravField = readBoolean(packer, true, send) ? engine.createComponent(GravitationalFieldComponent.class) : null;
            }
            else {
                // duplicate entity
                Log.warn("Entity already exists " + entityId);
                // TODO: remove extra components
                iden = ComponentMappers.identity.get(entity);
                pb = readBoolean(packer, true, send) ? ComponentMappers.physicsBody.get(entity) : null;
                graphics = readBoolean(packer, true, send) ? ComponentMappers.graphics.get(entity) : null;
                hazard = readBoolean(packer, true, send) ? ComponentMappers.hazards.get(entity) : null;
                gravField = readBoolean(packer, true, send) ? ComponentMappers.gravFields.get(entity) : null;
            }

            if (pb != null) {
                pb.bodyDef = new BodyDef();
                pb.fixtureDef = new FixtureDef();
                pb.shapeDef = new ShapeDef();
            }
            if (graphics != null) {
                graphics.textureDef = new TextureDef();
            }
        }

        iden.id = entityId;
        iden.read(packer, engine, send);
        if (pb != null) pb.read(packer, engine, send);
        if (graphics != null) graphics.read(packer, engine, send);
        if (hazard != null) hazard.read(packer, engine, send);
        if (gravField != null) gravField.read(packer, engine, send);

        if (!send) {
            if (entity == null) {
                entity = engine.createEntity();
                entity.add(iden);
                if (pb != null) entity.add(pb);
                if (graphics != null) entity.add(graphics);
                if (hazard != null) entity.add(hazard);
                if (gravField != null) entity.add(gravField);
                engine.addEntity(entity);
            }
        }
    }
}
