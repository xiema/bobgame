package com.xam.bobgame.events;

import com.xam.bobgame.net.NetDriver;

public class PlayerBallSpawnedEvent extends NetDriver.NetworkEvent {

    public int playerId = -1;
    public int entityId = -1;

    @Override
    public void reset() {
        super.reset();
        playerId = -1;
        entityId = -1;
    }
}
