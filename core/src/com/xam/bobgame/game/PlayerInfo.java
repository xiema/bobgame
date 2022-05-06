package com.xam.bobgame.game;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.net.NetSerializable;
import com.xam.bobgame.utils.BitPacker;

public class PlayerInfo implements NetSerializable {

    public boolean inPlay = false;
    public int controlledEntityId = -1;
    public int score = 0;
    public float respawnTime = 0;

    public void reset() {
        inPlay = false;
        controlledEntityId = -1;
        score = 0;
        respawnTime = 0;
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        inPlay = packer.readBoolean(inPlay);
        controlledEntityId = packer.readInt(controlledEntityId, -1, NetDriver.MAX_ENTITY_ID);
        score = packer.readInt(score, NetDriver.MIN_SCORE, NetDriver.MAX_SCORE);
        respawnTime = packer.readFloat(respawnTime, 0, GameProperties.PLAYER_RESPAWN_TIME, 0.125f);

        return 0;
    }
}
