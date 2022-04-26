package com.xam.bobgame.events;

public class PlayerJoinedEvent implements GameEvent {

    public int playerId = -1;
    public String address;

    @Override
    public void reset() {
        playerId = -1;
    }
}
