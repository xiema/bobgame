package com.xam.bobgame.events;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class PlayerLeftEvent extends NetDriver.NetworkEvent {

    public int playerId = -1;
    public boolean kicked = false;

    @Override
    public void reset() {
        playerId = -1;
    }

    @Override
    public NetDriver.NetworkEvent copyTo(NetDriver.NetworkEvent event) {
        PlayerLeftEvent other = (PlayerLeftEvent) event;
        other.playerId = playerId;
        return super.copyTo(event);
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        playerId = packer.readInt(playerId, 0, NetDriver.MAX_CLIENTS - 1);
        kicked = packer.readBoolean(kicked);
        return 0;
    }

    @Override
    public String toString() {
        return "PlayerLeftEvent playerId=" + playerId;
    }
}
