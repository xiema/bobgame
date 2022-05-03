package com.xam.bobgame.events;

public class ButtonReleaseEvent implements GameEvent {
    public float x = 0;
    public float y = 0;
    public int playerId = -1;
    public float holdDuration = -1;

    @Override
    public void reset() {
        playerId = -1;
        holdDuration = -1;
    }
}
