package com.xam.bobgame.events.classes;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class RequestPlayerIdEvent extends NetDriver.NetworkEvent {
    // TODO: use different interface or superclass

    @Override
    public int read(BitPacker packer, Engine engine) {
        return 0;
    }
}
