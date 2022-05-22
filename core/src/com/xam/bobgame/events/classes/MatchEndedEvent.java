package com.xam.bobgame.events.classes;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class MatchEndedEvent extends NetDriver.NetworkEvent {

    public int winningPlayerId = -1;

    @Override
    public int read(BitPacker packer, Engine engine) {
        winningPlayerId = packer.readInt(winningPlayerId, 0, NetDriver.MAX_CLIENTS - 1);
        return 0;
    }

    @Override
    public NetDriver.NetworkEvent copyTo(NetDriver.NetworkEvent event) {
        MatchEndedEvent other = (MatchEndedEvent) event;
        other.winningPlayerId = winningPlayerId;
        return super.copyTo(event);
    }

    @Override
    public void reset() {
        super.reset();
        winningPlayerId = -1;
    }
}
