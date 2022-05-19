package com.xam.bobgame.events.classes;

import com.xam.bobgame.events.GameEvent;

public class ClientDisconnectedEvent implements GameEvent {

    public int clientId = -1;
    public int playerId = -1;
    public boolean cleanDisconnect = true;

    @Override
    public void reset() {
        clientId = -1;
        playerId = -1;
        cleanDisconnect = true;
    }

    @Override
    public String toString() {
        return "ClientConnectedEvent clientId=" + clientId;
    }
}
