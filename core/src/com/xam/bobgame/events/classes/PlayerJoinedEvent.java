package com.xam.bobgame.events.classes;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class PlayerJoinedEvent extends NetDriver.NetworkEvent {

    public int playerId = -1;

    @Override
    public void reset() {
        playerId = -1;
    }

    @Override
    public NetDriver.NetworkEvent copyTo(NetDriver.NetworkEvent event) {
        PlayerJoinedEvent other = (PlayerJoinedEvent) event;
        other.playerId = playerId;
        return super.copyTo(event);
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        playerId = packer.readInt(playerId, 0, NetDriver.MAX_CLIENTS - 1);
        return 0;
    }

    @Override
    public String toString() {
        return "PlayerJoinedEvent playerId=" + playerId;
    }
}
