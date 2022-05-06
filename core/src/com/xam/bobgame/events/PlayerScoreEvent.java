package com.xam.bobgame.events;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class PlayerScoreEvent extends NetDriver.NetworkEvent {

    public int playerId = -1;
    public int scoreIncrement = 0;

    @Override
    public void reset() {
        super.reset();
        playerId = -1;
        scoreIncrement = 0;
    }

    @Override
    public NetDriver.NetworkEvent copyTo(NetDriver.NetworkEvent event) {
        PlayerScoreEvent other = (PlayerScoreEvent) event;
        other.playerId = playerId;
        other.scoreIncrement = scoreIncrement;
        return super.copyTo(event);
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        playerId = packer.readInt(playerId, 0, NetDriver.MAX_CLIENTS - 1);
        scoreIncrement = packer.readInt(scoreIncrement, 0, NetDriver.MAX_SCORE_INCREMENT);
        return 0;
    }
}
