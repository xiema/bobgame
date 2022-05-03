package com.xam.bobgame.events;

import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class ScoreBoardUpdateEvent extends NetDriver.NetworkEvent {

    public int playerId = -1;

    @Override
    public void read(BitPacker builder, boolean write) {
        playerId = readInt(builder, playerId, -1, 31, write);
    }
}
