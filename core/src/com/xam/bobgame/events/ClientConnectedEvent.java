package com.xam.bobgame.events;

public class ClientConnectedEvent implements GameEvent {

    public int connectionId = -1;

    @Override
    public void reset() {
        connectionId = -1;
    }
}
