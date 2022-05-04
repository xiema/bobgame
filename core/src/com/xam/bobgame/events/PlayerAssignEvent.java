package com.xam.bobgame.events;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.utils.BitPacker;
import com.xam.bobgame.net.NetDriver;

public class PlayerAssignEvent extends NetDriver.NetworkEvent {

    public int playerId = -1;
    public int entityId = -1;

    @Override
    public void reset() {
        super.reset();
        entityId = -1;
        playerId = -1;
    }

    @Override
    public void read(BitPacker builder, Engine engine, boolean write) {
        playerId = readInt(builder, playerId, -1, 31, write);
        entityId = readInt(builder, entityId, 0, NetDriver.MAX_ENTITY_ID, write);
    }

    @Override
    public String toString() {
        return "PlayerAssignEvent playerId=" + playerId + " entityId=" + entityId;
    }
}
