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
    public void read(BitPacker packer, Engine engine, boolean send) {
        playerId = readInt(packer, playerId, -1, 31, send);
        entityId = readInt(packer, entityId, 0, NetDriver.MAX_ENTITY_ID, send);
    }

    @Override
    public String toString() {
        return "PlayerAssignEvent playerId=" + playerId + " entityId=" + entityId;
    }
}
