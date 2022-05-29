package com.xam.bobgame.events.classes;

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
    public boolean snapshot = false;

    @Override
    public void reset() {
        super.reset();
        entityId = -1;
        snapshot = false;
    }

    @Override
    public NetDriver.NetworkEvent copyTo(NetDriver.NetworkEvent event) {
        EntityCreatedEvent other = (EntityCreatedEvent) event;
        other.entityId = entityId;
        other.snapshot = snapshot;
        return super.copyTo(event);
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        Entity entity;
        IdentityComponent iden;
        PhysicsBodyComponent pb;
        GraphicsComponent graphics;
        HazardComponent hazard;
        GravitationalFieldComponent gravField;
        BuffableComponent buffable;

        if (packer.isWriteMode()) {
            entity = ((GameEngine) engine).getEntityById(entityId);
            if (entity == null) {
                Log.error("EntityCreatedEvent", "Entity with id " + entityId + " not found");
                return -1;
            }

            packer.packInt(entityId, 0, NetDriver.MAX_ENTITY_ID);
            iden = ComponentMappers.identity.get(entity);
            pb = ComponentMappers.physicsBody.get(entity);
            graphics = ComponentMappers.graphics.get(entity);
            hazard = ComponentMappers.hazards.get(entity);
            gravField = ComponentMappers.gravFields.get(entity);
            buffable = ComponentMappers.buffables.get(entity);

            packer.readBoolean(pb != null);
            packer.readBoolean(graphics != null);
            packer.readBoolean(hazard != null);
            packer.readBoolean(gravField != null);
            packer.readBoolean(buffable != null);
        }
        else {
            entityId = packer.unpackInt(0, NetDriver.MAX_ENTITY_ID);
            entity = ((GameEngine) engine).getEntityById(entityId);
            if (entity == null) {
                iden = engine.createComponent(IdentityComponent.class);
                pb = packer.readBoolean(true) ? engine.createComponent(PhysicsBodyComponent.class) : null;
                graphics = packer.readBoolean(true) ? engine.createComponent(GraphicsComponent.class) : null;
                hazard = packer.readBoolean(true) ? engine.createComponent(HazardComponent.class) : null;
                gravField = packer.readBoolean(true) ? engine.createComponent(GravitationalFieldComponent.class) : null;
                buffable = packer.readBoolean(true) ? engine.createComponent(BuffableComponent.class) : null;
            }
            else {
                if (!snapshot) {
                    // duplicate entity
                    Log.warn("Entity already exists " + entityId);
                }
                // TODO: remove extra components
                iden = ComponentMappers.identity.get(entity);
                pb = packer.readBoolean(true) ? ComponentMappers.physicsBody.get(entity) : null;
                graphics = packer.readBoolean(true) ? ComponentMappers.graphics.get(entity) : null;
                hazard = packer.readBoolean(true) ? ComponentMappers.hazards.get(entity) : null;
                gravField = packer.readBoolean(true) ? ComponentMappers.gravFields.get(entity) : null;
                buffable = packer.readBoolean(true) ? ComponentMappers.buffables.get(entity) : null;
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
        iden.read(packer, engine);
        if (pb != null) pb.read(packer, engine);
        if (graphics != null) graphics.read(packer, engine);
        if (hazard != null) hazard.read(packer, engine);
        if (gravField != null) gravField.read(packer, engine);
        if (buffable != null) buffable.read(packer, engine);

        if (packer.isReadMode()) {
            if (entity == null) {
                entity = engine.createEntity();
                entity.add(iden);
                if (pb != null) entity.add(pb);
                if (graphics != null) entity.add(graphics);
                if (hazard != null) entity.add(hazard);
                if (gravField != null) entity.add(gravField);
                if (buffable != null) entity.add(buffable);
                engine.addEntity(entity);
            }
        }

        return 0;
    }
}
