package com.xam.bobgame.events.classes;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class MatchEndedEvent extends NetDriver.NetworkEvent {

    @Override
    public int read(BitPacker packer, Engine engine) {
        return 0;
    }

    @Override
    public NetDriver.NetworkEvent copyTo(NetDriver.NetworkEvent event) {
        return super.copyTo(event);
    }

    @Override
    public void reset() {
        super.reset();
    }
}
