package com.xam.bobgame.events;

public abstract class EventListenerAdapter<T extends GameEvent> implements GameEventListener {

    @Override
    public void handle(GameEvent event) {
        //noinspection unchecked
        handleEvent((T) event);
    }

    public abstract void handleEvent(T event);
}
