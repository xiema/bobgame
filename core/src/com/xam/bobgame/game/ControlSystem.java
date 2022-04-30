package com.xam.bobgame.game;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameDirector;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.events.*;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.DebugUtils;

public class ControlSystem extends EntitySystem {
    private IntSet idSet = new IntSet();
    private IntArray[] controlMap = new IntArray[32];

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    public ControlSystem(int priority) {
        super(priority);

        listeners.put(PlayerControlEvent.class, new EventListenerAdapter<PlayerControlEvent>() {
            @Override
            public void handleEvent(PlayerControlEvent event) {
                control(event.controlId, event.entityId, event.x, event.y, event.buttonId, event.buttonState);
            }
        });

        for (int i = 0; i < controlMap.length; ++i) controlMap[i] = new IntArray(false, 4);
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
        idSet.clear();
        for (IntArray entityIdArray : controlMap) entityIdArray.clear();
    }

    public boolean registerEntity(int entityId, int controlId) {
        Log.info("Register entity " + entityId + " to player " + controlId);
        if (idSet.contains(entityId)) {
            DebugUtils.error("ControlSystem", "Attempted to register duplicate entity");
            return false;
        }

        idSet.add(entityId);
        controlMap[controlId].add(entityId);

        return true;
    }

    public void clearRegistry() {
        idSet.clear();
        for (IntArray entityIds : controlMap) entityIds.clear();
    }

    private Vector2 tempVec = new Vector2();

    private void control(int controlId, int entityId, float x, float y, int buttonId, boolean buttonState) {
        if (controlId < 0 || controlId >= controlMap.length) {
            DebugUtils.error("ControlSystem", "Invalid controlId: " + controlId);
            return;
        }
        Entity entity = getEngine().getSystem(GameDirector.class).getEntityById(entityId);
        if (entity == null) {
            DebugUtils.error("ControlSystem", "Invalid entityId: " + entityId);
            return;
        }

        if (buttonState) {
            PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
            tempVec.set(x, y).sub(pb.body.getPosition()).nor().scl(500f);
            pb.body.applyForceToCenter(tempVec, true);
        }
    }

    public IntArray getControlledEntityIds(int controlId) {
        return controlMap[controlId];
    }
}
