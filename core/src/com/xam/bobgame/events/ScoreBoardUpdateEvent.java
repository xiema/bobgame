package com.xam.bobgame.events;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class ScoreBoardUpdateEvent extends NetDriver.NetworkEvent {

    public int playerId = -1;

    @Override
    public void read(BitPacker builder, Engine engine, boolean write) {
        playerId = readInt(builder, playerId, -1, 31, write);
    }

    @Override
    public NetDriver.NetworkEvent copyTo(NetDriver.NetworkEvent event) {
        ScoreBoardUpdateEvent other = (ScoreBoardUpdateEvent) event;
        other.playerId = playerId;
        return super.copyTo(event);
    }

    @Override
    public void reset() {
        super.reset();
        playerId = -1;
    }
}
