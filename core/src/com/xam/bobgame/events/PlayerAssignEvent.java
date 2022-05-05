package com.xam.bobgame.events;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.utils.BitPacker;
import com.xam.bobgame.net.NetDriver;

public class PlayerAssignEvent extends NetDriver.NetworkEvent {

    public int playerId = -1;

    @Override
    public void reset() {
        super.reset();
        playerId = -1;
    }

    @Override
    public void read(BitPacker packer, Engine engine, boolean send) {
        playerId = readInt(packer, playerId, 0, NetDriver.MAX_CLIENTS - 1, send);
    }

    @Override
    public String toString() {
        return "PlayerAssignEvent playerId=" + playerId;
    }
}
