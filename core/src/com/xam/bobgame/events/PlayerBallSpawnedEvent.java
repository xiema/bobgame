package com.xam.bobgame.events;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class PlayerBallSpawnedEvent extends NetDriver.NetworkEvent {

    public int playerId = -1;
    public int entityId = -1;

    @Override
    public void reset() {
        super.reset();
        playerId = -1;
        entityId = -1;
    }

    @Override
    public void read(BitPacker packer, Engine engine, boolean send) {
        playerId = readInt(packer, playerId, 0, NetDriver.MAX_CLIENTS - 1, send);
        entityId = readInt(packer, entityId, 0, NetDriver.MAX_ENTITY_ID, send);
    }
}
