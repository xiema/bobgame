package com.xam.bobgame.events;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class RequestJoinEvent extends NetDriver.NetworkEvent {

    @Override
    public int read(BitPacker packer, Engine engine) {
        return 0;
    }
}
