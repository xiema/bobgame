package com.xam.bobgame.events.classes;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.utils.BitPacker;
import com.xam.bobgame.net.NetDriver;

public class PlayerAssignEvent extends NetDriver.NetworkEvent {

    public int playerId = -1;

    @Override
    public void reset() {
        super.reset();
        playerId = -1;
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        playerId = packer.readInt(playerId, 0, NetDriver.MAX_CLIENTS - 1);
        return 0;
    }

    @Override
    public NetDriver.NetworkEvent copyTo(NetDriver.NetworkEvent event) {
        PlayerAssignEvent other = (PlayerAssignEvent) event;
        other.playerId = playerId;
        return super.copyTo(event);
    }

    @Override
    public String toString() {
        return "PlayerAssignEvent playerId=" + playerId;
    }
}
