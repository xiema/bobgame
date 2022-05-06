package com.xam.bobgame.events;

public class ClientDisconnectedEvent implements GameEvent {

    public int clientId = -1;
    public int playerId = -1;

    @Override
    public void reset() {
        clientId = -1;
        playerId = -1;
    }

    @Override
    public String toString() {
        return "ClientConnectedEvent clientId=" + clientId;
    }
}