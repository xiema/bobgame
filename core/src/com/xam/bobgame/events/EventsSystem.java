package com.xam.bobgame.events;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.minlog.Log;

public class EventsSystem extends EntitySystem {

    private final Array<GameEvent> eventQueue = new Array<>();
    private ObjectMap<Class<? extends GameEvent>, Array<GameEventListener>> listenerMap = new ObjectMap<>();
    private Array<GameEventListener> globalListeners = new Array<>();

    public EventsSystem(int priority) {
        super(priority);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        super.removedFromEngine(engine);
        synchronized (eventQueue) {
            for (GameEvent event : eventQueue) {
                Pools.free(event);
            }
            eventQueue.clear();
        }
        listenerMap.clear();
        globalListeners.clear();
    }

    Array<GameEvent> currentQueue = new Array<>();
    @Override
    public void update(float deltaTime) {
        synchronized (eventQueue) {
            currentQueue.addAll(eventQueue);
            eventQueue.clear();
        }

        for (GameEvent event : currentQueue) {
            Array<GameEventListener> listeners = listenerMap.get(event.getClass());
            if (listeners != null) {
                handleEvent(event, listeners);
            }
            for (GameEventListener listener : globalListeners) {
                listener.handle(event);
            }
            Pools.free(event);
        }

        currentQueue.clear();
    }

    private void handleEvent(GameEvent event, Array<GameEventListener> eventListeners) {
        for (GameEventListener listener : eventListeners) {
//            Log.info("EventsSystem", event.getClass().getSimpleName() + ": handled by " + listener);
            listener.handle(event);
        }
    }

    public int queueEvent(GameEvent event) {
        Array<GameEventListener> listeners = listenerMap.get(event.getClass());
        if ((listeners == null || listeners.isEmpty()) && globalListeners.isEmpty()) {
            Pools.free(event);
            return 0;
        }
        synchronized (eventQueue) {
            eventQueue.add(event);
        }
        return globalListeners.size + (listeners == null ? 0 : listeners.size);
    }

    // should be used sparingly (only for specific event types between tightly-coupled systems)
    public int triggerEvent(GameEvent event) {
        Array<GameEventListener> listeners = listenerMap.get(event.getClass());
        if ((listeners == null || listeners.isEmpty()) && globalListeners.isEmpty()) {
            Pools.free(event);
            return 0;
        }

        if (listeners != null) handleEvent(event, listeners);
        for (GameEventListener listener : globalListeners) {
            listener.handle(event);
        }
        Pools.free(event);
        return globalListeners.size + (listeners == null ? 0 : listeners.size);
    }

    public void addGlobalListener(GameEventListener listener) {
        if (!globalListeners.contains(listener, true)) globalListeners.add(listener);
    }

    public void addListener(Class<? extends GameEvent> type, GameEventListener listener) {
        Array<GameEventListener> listeners = listenerMap.get(type);
        if (listeners == null) {
            listeners = new Array<>();
            listenerMap.put(type, listeners);
        }
        if (listeners.contains(listener, true)) {
            Log.error("EventsSystem", "Attempted to register a duplicate listener");
            return;
        }
        listeners.add(listener);
    }

    public void addListeners(ObjectMap<Class<? extends GameEvent>,GameEventListener> listenersToAdd) {
        for (ObjectMap.Entry<Class<? extends GameEvent>, GameEventListener> entry : listenersToAdd.entries()) {
            addListener(entry.key, entry.value);
        }
    }

    public void removeGlobalListener(GameEventListener listener) {
        globalListeners.removeValue(listener, true);
    }

    public void removeListener(Class<? extends GameEvent> type, GameEventListener listener) {
        Array<GameEventListener> listeners = listenerMap.get(type);
        if (listeners != null) {
            if (listeners.removeValue(listener, true)) {
                return;
            }
        }
//        DebugUtils.debug("EventsSystem", "Attempted to remove an unknown listener");
    }

    public void removeListeners(ObjectMap<Class<? extends GameEvent>,GameEventListener> listenersToRemove) {
        for (ObjectMap.Entry<Class<? extends GameEvent>, GameEventListener> entry : listenersToRemove.entries()) {
            removeListener(entry.key, entry.value);
        }
    }
}
