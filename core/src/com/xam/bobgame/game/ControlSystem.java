package com.xam.bobgame.game;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameDirector;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.events.*;
import com.xam.bobgame.net.NetDriver;

import java.util.Arrays;

public class ControlSystem extends EntitySystem {
    private IntSet idSet = new IntSet();
    private IntArray[] controlMap = new IntArray[NetDriver.MAX_CLIENTS];

    private float[] buttonHoldDurations = new float[NetDriver.MAX_CLIENTS];
    private boolean[] buttonStates = new boolean[NetDriver.MAX_CLIENTS];

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    private boolean enabled = false;

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
        Arrays.fill(buttonHoldDurations, -1);
        Arrays.fill(buttonStates, false);
    }

    @Override
    public void removedFromEngine(Engine engine) {
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
        if (eventsSystem != null) eventsSystem.removeListeners(listeners);
        idSet.clear();
        for (IntArray entityIdArray : controlMap) entityIdArray.clear();
    }

    @Override
    public void update(float deltaTime) {
        for (int i = 0; i < buttonStates.length; ++i) {
            if (buttonStates[i]) {
                buttonHoldDurations[i] = ((buttonHoldDurations[i] < 0 ? 0 : buttonHoldDurations[i]) + GameProperties.SIMULATION_UPDATE_INTERVAL) % GameProperties.CHARGE_DURATION_2;
            }
        }
    }

    public boolean registerEntity(int entityId, int controlId) {
        Log.info("Register entity " + entityId + " to player " + controlId);
        if (idSet.contains(entityId)) {
            Log.error("ControlSystem", "Attempted to register duplicate entity");
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

    private void control(int controlId, int entityId, float x, float y, int buttonId, boolean buttonState) {
        if (controlId < 0 || controlId >= controlMap.length) {
            Log.error("ControlSystem", "Invalid controlId: " + controlId);
            return;
        }
        Entity entity = getEngine().getSystem(GameDirector.class).getEntityById(entityId);
        if (entity == null) {
            Log.error("ControlSystem", "Invalid entityId: " + entityId);
            return;
        }

        if (buttonId != 0) return;
        if (buttonStates[controlId] && !buttonState) {
            ButtonReleaseEvent event = Pools.obtain(ButtonReleaseEvent.class);
            event.playerId = controlId;
            event.holdDuration = buttonHoldDurations[controlId];
            event.x = x;
            event.y = y;
            getEngine().getSystem(EventsSystem.class).triggerEvent(event);
            buttonHoldDurations[controlId] = -NetDriver.RES_HOLD_DURATION;
        }
        buttonStates[controlId] = buttonState;
    }

    public IntArray getControlledEntityIds(int controlId) {
        return controlMap[controlId];
    }

    public boolean[] getButtonStates() {
        return buttonStates;
    }

    public float[] getButtonHoldDurations() {
        return buttonHoldDurations;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
