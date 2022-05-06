package com.xam.bobgame.events;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class ScoreBoardRefreshEvent extends NetDriver.NetworkEvent {

    @Override
    public void read(BitPacker packer, Engine engine, boolean send) {
        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
        boolean[] playerExists = refereeSystem.getPlayerExists();
        int[] playerControlMap = refereeSystem.getPlayerControlMap();
        int[] playerScores = refereeSystem.getPlayerScores();

        for (int i = 0; i < playerControlMap.length; ++i) {
            playerExists[i] = readBoolean(packer, playerExists[i], send);
            playerControlMap[i] = readInt(packer, playerControlMap[i], -1, NetDriver.MAX_ENTITY_ID, send);
            playerScores[i] = readInt(packer, playerScores[i], NetDriver.MIN_SCORE, NetDriver.MAX_SCORE, send);
        }
    }
}
