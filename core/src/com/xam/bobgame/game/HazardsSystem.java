package com.xam.bobgame.game;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.utils.ObjectMap;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.events.*;

public class HazardsSystem extends EntitySystem {

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    private boolean enabled = false;

    public HazardsSystem(int priority) {
        super(priority);

        listeners.put(HazardContactEvent.class, new EventListenerAdapter<HazardContactEvent>() {
            @Override
            public void handleEvent(HazardContactEvent event) {
                if (enabled) {
                    RefereeSystem refereeSystem = getEngine().getSystem(RefereeSystem.class);
                    int entityId = EntityUtils.getId(event.entity);
                    if (entityId == -1) return;
                    int playerId = refereeSystem.getEntityPlayerId(entityId);
                    if (playerId == -1) return;
                    refereeSystem.killPlayer(playerId);
                }
            }
        });
    }

    @Override
    public void addedToEngine(Engine engine) {
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
        eventsSystem.addListeners(listeners);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
        if (eventsSystem != null) eventsSystem.removeListeners(listeners);
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
