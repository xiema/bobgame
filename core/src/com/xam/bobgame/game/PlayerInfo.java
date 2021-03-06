package com.xam.bobgame.game;

import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.net.NetSerializable;
import com.xam.bobgame.utils.BitPacker;

import java.util.Comparator;

public class PlayerInfo implements NetSerializable {

    public final int playerId;

    public boolean connected = false;
    public float latency = 0;
    public boolean inPlay = false;
    public int controlledEntityId = -1;
    public int score = 0;
    public float respawnTime = 0;
    public float stamina = 100;

    public int rank = -1;

    public PlayerInfo(int playerId) {
        this.playerId = playerId;
    }

    public void reset() {
        connected = false;
        latency = 0;
        inPlay = false;
        controlledEntityId = -1;
        score = 0;
        respawnTime = 0;
        stamina = 100;
        rank = -1;
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        connected = packer.readBoolean(connected);
        if (!connected) {
            reset();
            return 0;
        }
        latency = packer.readFloat(latency, 0, NetDriver.MAX_LATENCY, NetDriver.RES_LATENCY);
        inPlay = packer.readBoolean(inPlay);
        if (!inPlay) {
            float tmp = latency;
            reset();
            connected = true;
            latency = tmp;
            return 0;
        }
        controlledEntityId = packer.readInt(controlledEntityId, -1, NetDriver.MAX_ENTITY_ID);
        score = packer.readInt(score, NetDriver.MIN_SCORE, NetDriver.MAX_SCORE);
        respawnTime = packer.readFloat(respawnTime, 0, GameProperties.PLAYER_RESPAWN_TIME, 0.125f);
        stamina = packer.readFloat(stamina, GameProperties.PLAYER_STAMINA_MIN, GameProperties.PLAYER_STAMINA_MAX, 0.125f);

        return 0;
    }

    public static final Comparator<PlayerInfo> COMPARATOR = new Comparator<PlayerInfo>() {
        @Override
        public int compare(PlayerInfo p1, PlayerInfo p2) {
            return Integer.compare(p1.score, p2.score);
        }
    };
}
