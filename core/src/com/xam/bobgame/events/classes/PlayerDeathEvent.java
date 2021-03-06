package com.xam.bobgame.events.classes;

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
    public NetDriver.NetworkEvent copyTo(NetDriver.NetworkEvent event) {
        PlayerDeathEvent other = (PlayerDeathEvent) event;
        other.playerId = playerId;
        other.entityId = entityId;
        return super.copyTo(event);
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        playerId = packer.readInt(playerId, 0, NetDriver.MAX_CLIENTS - 1);
        entityId = packer.readInt(entityId, 0, NetDriver.MAX_ENTITY_ID);
        return 0;
    }
}
