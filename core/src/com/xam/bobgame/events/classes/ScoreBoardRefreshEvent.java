package com.xam.bobgame.events.classes;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.game.PlayerInfo;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class ScoreBoardRefreshEvent extends NetDriver.NetworkEvent {

    @Override
    public int read(BitPacker packer, Engine engine) {
        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);

        for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
            PlayerInfo playerInfo = refereeSystem.getPlayerInfo(i);
            playerInfo.inPlay = packer.readBoolean(playerInfo.inPlay);
            playerInfo.controlledEntityId = packer.readInt(playerInfo.controlledEntityId, -1, NetDriver.MAX_ENTITY_ID);
            playerInfo.score = packer.readInt(playerInfo.score, NetDriver.MIN_SCORE, NetDriver.MAX_SCORE);
        }

        return 0;
    }
}
