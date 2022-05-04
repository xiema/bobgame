package com.xam.bobgame.events;

import com.badlogic.ashley.core.Engine;
import com.esotericsoftware.minlog.Log;
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
    public void read(BitPacker builder, Engine engine, boolean write) {
        playerId = readInt(builder, playerId, 0, NetDriver.MAX_CLIENTS - 1, write);
    }

    @Override
    public String toString() {
        return "PlayerJoinedEvent playerId=" + playerId;
    }
}
