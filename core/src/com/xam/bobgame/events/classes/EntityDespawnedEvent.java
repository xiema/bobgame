package com.xam.bobgame.events.classes;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class EntityDespawnedEvent extends NetDriver.NetworkEvent {
    public int entityId = -1;

    @Override
    public void reset() {
        super.reset();
        entityId = -1;
    }

    @Override
    public NetDriver.NetworkEvent copyTo(NetDriver.NetworkEvent event) {
        EntityDespawnedEvent other = (EntityDespawnedEvent) event;
        other.entityId = entityId;
        return super.copyTo(event);
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        entityId = packer.readInt(entityId, 0, NetDriver.MAX_ENTITY_ID);

        if (packer.isReadMode()) {
            Entity entity = ((GameEngine) engine).getEntityById(entityId);
            if (entity == null) {
                Log.warn("EntityDespawnedEvent", "No entity found with id " + entityId);
                return 0;
            }
            engine.removeEntity(entity);
        }

        return 0;
    }
}
