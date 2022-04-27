package com.xam.bobgame.events;

import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.net.Packet;

public class PlayerAssignEvent extends NetDriver.NetworkEvent {

    public int entityId = -1;

    @Override
    public void reset() {
        super.reset();
        entityId = -1;
    }

    @Override
    public void read(Packet.PacketBuilder builder, boolean write) {
        entityId = readInt(builder, entityId, 0, 255, write);
    }
}
