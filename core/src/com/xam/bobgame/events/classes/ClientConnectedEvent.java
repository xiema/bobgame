package com.xam.bobgame.events.classes;

import com.xam.bobgame.events.GameEvent;

public class ClientConnectedEvent implements GameEvent {

    public int clientId = -1;

    @Override
    public void reset() {
        clientId = -1;
    }

    @Override
    public String toString() {
        return "ClientConnectedEvent clientId=" + clientId;
    }
}
