package com.xam.bobgame.events;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class PlayerDeathEvent extends NetDriver.NetworkEvent {

    public int playerId = -1;
    public int entityId = -1;

    @Override
    public void reset() {
        super.reset();
        playerId = -1;
        entityId = -1;
    }

    @Override
    public void read(BitPacker builder, Engine engine, boolean write) {
        playerId = readInt(builder, playerId, 0, NetDriver.MAX_CLIENTS - 1, write);
        entityId = readInt(builder, entityId, 0, NetDriver.MAX_ENTITY_ID, write);
    }
}
