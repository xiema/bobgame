package com.xam.bobgame.game;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.ObjectMap;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.events.*;
import com.xam.bobgame.utils.DebugUtils;

public class ControlSystem extends EntitySystem {
    private IntSet idSet = new IntSet();
    private IntMap<IntMap<Entity>> controlMap = new IntMap<>();

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    public ControlSystem(int priority) {
        super(priority);

        listeners.put(PlayerControlEvent.class, new EventListenerAdapter<PlayerControlEvent>() {
            @Override
            public void handleEvent(PlayerControlEvent event) {
                control(event.controlId, event.entityId, event.x, event.y);
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
        eventsSystem.removeListeners(listeners);
        idSet.clear();
        controlMap.clear();
    }

    public boolean registerEntity(Entity entity, int controlId) {
        int id = EntityUtils.getId(entity);
        if (idSet.contains(id)) {
            DebugUtils.error("ControlSystem", "Attempted to register duplicate entity");
            return false;
        }

        IntMap<Entity> entityMap = controlMap.get(controlId, null);
        if (entityMap == null) controlMap.put(controlId, entityMap = new IntMap<>());
        entityMap.put(id, entity);

        return true;
    }

    private Vector2 tempVec = new Vector2();

    private void control(int controlId, int entityId, float x, float y) {
        IntMap<Entity> entityMap = controlMap.get(controlId, null);
        if (entityMap == null) {
            DebugUtils.error("ControlSystem", "Invalid controlId: " + controlId);
            return;
        }
        Entity entity = entityMap.get(entityId, null);
        if (entity == null) {
            DebugUtils.error("ControlSystem", "Invalid entityId: " + entityId);
            return;
        }
    }
}
