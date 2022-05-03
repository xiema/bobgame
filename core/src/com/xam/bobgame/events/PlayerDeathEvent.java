package com.xam.bobgame.events;

public class PlayerDeathEvent implements GameEvent {

    public int playerId = -1;
    public int playerScore = 0;

    @Override
    public void reset() {
        playerId = -1;
        playerScore = 0;
    }
}
