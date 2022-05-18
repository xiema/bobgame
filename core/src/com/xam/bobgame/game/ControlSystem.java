package com.xam.bobgame.game;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Transform;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.events.*;
import com.xam.bobgame.net.NetDriver;

public class ControlSystem extends EntitySystem {
    private IntSet idSet = new IntSet();

    private PlayerControlInfo[] playerControlInfos = new PlayerControlInfo[NetDriver.MAX_CLIENTS];

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    private boolean enabled = false;
    private boolean controlFacing = false;

    private Vector2 localMouseVec = new Vector2();
    private boolean localButtonState = false;
    private int localButton = 0;

    public ControlSystem(int priority) {
        super(priority);

        listeners.put(PlayerControlEvent.class, new EventListenerAdapter<PlayerControlEvent>() {
            @Override
            public void handleEvent(PlayerControlEvent event) {
                control(event.controlId, event.entityId, event.x, event.y, event.buttonId, event.buttonState);
            }
        });
        listeners.put(PlayerDeathEvent.class, new EventListenerAdapter<PlayerDeathEvent>() {
            @Override
            public void handleEvent(PlayerDeathEvent event) {
                playerControlInfos[event.playerId].holdDuration = 0;
                playerControlInfos[event.playerId].buttonState = false;
            }
        });

        for (int i = 0; i < playerControlInfos.length; ++i) playerControlInfos[i] = new PlayerControlInfo();
    }

    @Override
    public void addedToEngine(Engine engine) {
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
        eventsSystem.addListeners(listeners);
        for (PlayerControlInfo playerControlInfo : playerControlInfos) playerControlInfo.reset();
    }

    @Override
    public void removedFromEngine(Engine engine) {
        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
        if (eventsSystem != null) eventsSystem.removeListeners(listeners);
        idSet.clear();
    }

    @Override
    public void update(float deltaTime) {
        RefereeSystem refereeSystem = getEngine().getSystem(RefereeSystem.class);
        if (refereeSystem.getLocalPlayerEntityId() != -1) {
            PlayerControlEvent playerControlEvent = Pools.obtain(PlayerControlEvent.class);
            tempVec.set(localMouseVec);
            ((GameEngine) getEngine()).getViewport().unproject(tempVec);
            playerControlEvent.x = tempVec.x;
            playerControlEvent.y = tempVec.y;
            playerControlEvent.buttonId = localButton;
            playerControlEvent.buttonState = localButtonState;
            playerControlEvent.controlId = refereeSystem.getLocalPlayerId();
            playerControlEvent.entityId = refereeSystem.getLocalPlayerEntityId();

            NetDriver netDriver = getEngine().getSystem(NetDriver.class);
            EventsSystem eventsSystem = getEngine().getSystem(EventsSystem.class);

            if (((GameEngine) getEngine()).getMode() == GameEngine.Mode.Client) {
                netDriver.queueClientEvent(-1, playerControlEvent);
            }
            eventsSystem.triggerEvent(playerControlEvent);
        }
        if (controlFacing) {
            for (int i = 0; i < playerControlInfos.length; ++i) updatePlayer(i);
        }
        else {
            updatePlayer(getEngine().getSystem(RefereeSystem.class).getLocalPlayerId());
        }
    }

    public void userInput(float x, float y, int button, boolean state) {
        localMouseVec.set(x, y);
        localButton = button;
        localButtonState = state;
    }

    private void updatePlayer(int playerId) {
        if (playerId == -1) return;
        Entity entity = getEngine().getSystem(RefereeSystem.class).getPlayerEntity(playerId);
        if (entity == null) return;

        PlayerControlInfo playerControlInfo = playerControlInfos[playerId];
        PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
        Transform tfm = pb.body.getTransform();
        tfm.setOrientation(tempVec.set(playerControlInfo.cursorPosition.x - tfm.vals[0], playerControlInfo.cursorPosition.y - tfm.vals[1]));
        pb.body.setTransform(tfm.getPosition(), tfm.getRotation());
    }

    private final Vector2 tempVec = new Vector2();

    private void control(int playerId, int entityId, float x, float y, int buttonId, boolean buttonState) {
        if (playerId < 0 || playerId >= NetDriver.MAX_CLIENTS) {
            Log.debug("ControlSystem", "Invalid playerId: " + playerId);
            return;
        }
        Entity entity = ((GameEngine) getEngine()).getEntityById(entityId);
        if (entity == null) {
//            Log.debug("ControlSystem", "Invalid entityId: " + entityId);
            return;
        }

        PlayerInfo playerInfo = getEngine().getSystem(RefereeSystem.class).getPlayerInfo(playerId);
        PlayerControlInfo playerControlInfo = playerControlInfos[playerId];
        playerControlInfo.cursorPosition.set(x, y);

        if (buttonId != 0) return;
        if (buttonState) {
//            if (!playerControlInfo.buttonState) {
//                Log.debug("ControlSystem", "Player " + playerId + " button down");
//            }
            playerControlInfo.holdDuration = (playerControlInfo.holdDuration + GameProperties.SIMULATION_UPDATE_INTERVAL) % GameProperties.CHARGE_DURATION_2;
        }
        else if (playerControlInfo.buttonState) {
            ButtonReleaseEvent event = Pools.obtain(ButtonReleaseEvent.class);
            event.playerId = playerId;
            event.holdDuration = Math.max(NetDriver.RES_HOLD_DURATION, playerControlInfo.holdDuration);
            event.x = x;
            event.y = y;
            getEngine().getSystem(EventsSystem.class).triggerEvent(event);
            playerControlInfo.holdDuration = 0;
        }
        playerControlInfo.buttonState = buttonState;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setControlFacing(boolean controlFacing) {
        this.controlFacing = controlFacing;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public PlayerControlInfo getPlayerControlInfo(int playerId) {
        return playerControlInfos[playerId];
    }
}
