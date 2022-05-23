package com.xam.bobgame.net;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.events.*;
import com.xam.bobgame.events.classes.*;
import com.xam.bobgame.game.*;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.components.*;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.utils.BitPacker;
import com.xam.bobgame.utils.OrderedIntMap;

/**
 * Message Serializer/Deserializer. Also keeps {@link MessageInfo} of sent messages.
 */
@SuppressWarnings("UnusedReturnValue")
public class MessageReader {

    private final NetDriver netDriver;
    private final BitPacker packer = new BitPacker();

    private final MessageInfo[] messageInfos = new MessageInfo[NetDriver.MAX_MESSAGE_HISTORY];
    private int messageIdCounter = 0;

    private final IntArray nonExistent = new IntArray(false, 4);
    private final IntArray notUpdated = new IntArray(false, 4);

    public MessageReader(NetDriver netDriver) {
        this.netDriver = netDriver;
        for (int i = 0; i < messageInfos.length; ++i) {
            messageInfos[i] = new MessageInfo();
        }
    }

    public void setMessageInfo(Message message) {
        message.messageId = messageIdCounter;
        message.frameNum = ((GameEngine) netDriver.getEngine()).getCurrentFrame();
        messageInfos[messageIdCounter % messageInfos.length].set(message);
        messageIdCounter++;
    }

    public MessageInfo getMessageInfo(int messageId) {
        return messageInfos[messageId % messageInfos.length];
    }

    public void consistencyCheck() {
        if (nonExistent.notEmpty()) {
            for (int i = 0; i < nonExistent.size; ++i) {
                int entityId = nonExistent.get(i);
                Log.warn("Received update for nonexistent entity " + entityId);
            }
            nonExistent.clear();
            netDriver.client.requestSnapshot();
        }

        if (notUpdated.notEmpty()) {
            for (int i = 0; i < notUpdated.size; ++i) {
                int entityId = notUpdated.get(i);
                Log.debug("Entity " + entityId + " was not updated");
                Entity entity = ((GameEngine) netDriver.getEngine()).getEntityById(entityId);
                if (entity != null) {
                    netDriver.getEngine().removeEntity(entity);
                }
                else {
                    Log.warn("Entity " + entityId + " was already removed");
                }
            }
            notUpdated.clear();
        }
    }

    public int deserialize(Message message, int clientId) {
        packer.setBuffer(message.getByteBuffer());
        packer.setReadMode();

        switch (message.getType()) {
            case Update:
                Message.UpdateType updateType = Message.UpdateType.values()[packer.readInt(-1, 0, Message.UpdateType.values().length - 1)];
                switch (updateType) {
                    case System:
                        readSystemUpdate();
                        break;
                    case Event:
                        readEvent(clientId);
                        break;
                }
                break;
            case Snapshot:
                Log.debug("Deserializing system snapshot...");
                readSystemSnapshot();
                break;
            case Input:
                readEvent(clientId);
                break;
            case Empty:
                // empty
                break;
            default:
                return -1;
        }
        packer.skipToWord();

        if (message.getByteBuffer().hasRemaining()) {
            Log.warn("Message has excess bytes: " + message);
        }

        return 0;
    }

    public int serialize(Message message, Message.MessageType type) {
        packer.setBuffer(message.getByteBuffer());
        packer.setWriteMode();

        message.setType(type);
        setMessageInfo(message);
        switch (type) {
            case Update:
                packer.packInt(Message.UpdateType.System.getValue(), 0, Message.UpdateType.values().length - 1);
                if (readSystemUpdate() == -1) return -1;
                break;
            case Snapshot:
                Log.debug("Serializing system snapshot...");
                if (readSystemSnapshot() == -1) return -1;
                break;
            case Empty:
                break;
        }
//        packer.padToWord();
        packer.flush(true);
        message.setLength(packer.getTotalBytes());

        return 0;
    }

    public int serializeEvent(Message message, NetDriver.NetworkEvent event) {
        packer.setBuffer(message.getByteBuffer());
        packer.setWriteMode();

        int typeIndex = NetDriver.getNetworkEventIndex(event.getClass());
        if (typeIndex == -1) {
            Log.error("Unknown typeIndex for " + event.getClass());
            return -1;
        }

        if (event instanceof PlayerControlEvent) {
            packer.packInt(typeIndex, 0, NetDriver.networkEventClasses.length - 1);
            event.read(packer, netDriver.getEngine());
//            packer.padToWord();
            packer.flush(true);
            message.setType(Message.MessageType.Input);
        }
        else {
            packer.packInt(Message.UpdateType.Event.getValue(), 0, Message.UpdateType.values().length - 1);
            packer.packInt(typeIndex, 0, NetDriver.networkEventClasses.length - 1);
            event.read(packer, netDriver.getEngine());
//            packer.padToWord();
            packer.flush(true);
            message.setType(Message.MessageType.Update);
        }
        message.eventTypes.add(event.getClass());
        message.setLength(packer.getTotalBytes());
        setMessageInfo(message);

//        Log.debug("MessagerReader.serializeEvent", "" + event + " : " + message);

        return 0;
    }

    private int readEvent(int clientId) {
        int type = packer.unpackInt(0, NetDriver.networkEventClasses.length - 1);
        if (type >= NetDriver.networkEventClasses.length) {
            Log.error("MessageReader", "Invalid network event id " + type + " from client " + clientId);
            return -1;
        }
        //noinspection unchecked
        NetDriver.NetworkEvent event = Pools.obtain((Class<? extends NetDriver.NetworkEvent>) NetDriver.networkEventClasses[type]);
        event.read(packer, netDriver.getEngine());

        if (event instanceof EntityDespawnedEvent) {
            EntityDespawnedEvent despawnedEvent = (EntityDespawnedEvent) event;
            notUpdated.removeValue(despawnedEvent.entityId);
        }
        else if (event instanceof EntityCreatedEvent) {
            EntityCreatedEvent entityCreatedEvent = (EntityCreatedEvent) event;
            nonExistent.removeValue(entityCreatedEvent.entityId);
        }
        // TODO: Maybe use separate event
        else if (event instanceof MatchRestartEvent) {
            notUpdated.clear();
        }

        if (packer.isReadMode()) {
            event.clientId = clientId;
//            Log.debug("MessageReader", "Read event " + event);
            netDriver.getEngine().getSystem(EventsSystem.class).queueEvent(event);
        }
//        packer.skipToWord();

        return 0;
    }

    private int readSystemUpdate() {
        readGameInfo();
        readPhysicsBodies();
        readControlStates();
        readPlayerInfos(false);

        return 0;
    }

    private final EntityCreatedEvent entityCreator = Pools.obtain(EntityCreatedEvent.class);

    private int readSystemSnapshot() {
        readGameInfo();
        ImmutableArray<Entity> entities = netDriver.getEngine().getEntities();
        int cnt = packer.readInt(entities.size(), 0, NetDriver.MAX_ENTITY_ID);
        int i = 0;
        while (cnt-- > 0) {
            if (packer.isWriteMode()) {
                Entity entity = entities.get(i++);
                entityCreator.entityId = EntityUtils.getId(entity);
            }
            // TODO: include entity position
            entityCreator.snapshot = true;
            entityCreator.read(packer, netDriver.getEngine());
        }

        readPlayerInfos(true);
        if (packer.isReadMode()) netDriver.getEngine().getSystem(RefereeSystem.class).refreshSortedPlayerInfos();

        return 0;
    }

    private int readGameInfo() {
        RefereeSystem refereeSystem = netDriver.getEngine().getSystem(RefereeSystem.class);
        RefereeSystem.MatchState matchState = RefereeSystem.MatchState.values()[packer.readInt(refereeSystem.getMatchState().value, 0, RefereeSystem.MatchState.values().length)];
        float matchTime = packer.readFloat(refereeSystem.getMatchTime(), 0, NetDriver.MAX_MATCH_TIME, NetDriver.RES_MATCH_TIME);
        float matchDuration = packer.readFloat(refereeSystem.getMatchDuration(), 0, NetDriver.MAX_MATCH_TIME, NetDriver.RES_MATCH_TIME);
        if (packer.isReadMode()) {
            refereeSystem.setMatchState(matchState);
            refereeSystem.setMatchTime(matchTime);
            refereeSystem.setMatchDuration(matchDuration);
        }
        return 0;
    }

    private int readPhysicsBodies() {
        OrderedIntMap<Entity> entityMap = ((GameEngine) netDriver.getEngine()).getEntityMap();

        if (packer.isWriteMode()) {
            packer.packInt(entityMap.size, 0, NetDriver.MAX_ENTITY_ID);
            for (int i = 0; i < entityMap.size; ++i) {
                int entityId = entityMap.getKey(i);
                Entity entity = entityMap.get(entityId, null);
                if (entity != null) {
                    packer.packInt(entityId, -1, NetDriver.MAX_ENTITY_ID);
                    PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
                    readPhysicsBody(pb);
                }
                else {
                    Log.warn("No entity with id " + entityId + " exists");
                    packer.packInt(-1, -1, NetDriver.MAX_ENTITY_ID);
                }
            }
        }
        else {
            int cnt = packer.unpackInt(0, NetDriver.MAX_ENTITY_ID);
            int j = 0;
            for (int i = 0; i < cnt; ++i) {
                int entityId = packer.unpackInt(-1, NetDriver.MAX_ENTITY_ID);
                if (entityId == -1) continue;
                while (j < entityMap.size && entityId > entityMap.getKey(j)) {
//                    Log.debug("Entity " + sortedEntityIds.get(j) + " skipped during update");
                    notUpdated.add(entityMap.getKey(j));
                    j++;
                }
                if (j < entityMap.size && entityId == entityMap.getKey(j)) {
                    j++;
                }
                else {
                    nonExistent.add(entityId);
                }
                Entity entity = entityMap.get(entityId, null);
                PhysicsBodyComponent pb = null;
                if (entity != null) {
                    pb = ComponentMappers.physicsBody.get(entity);
                    if (pb == null) {
                        Log.warn("Entity " + entityId + "has no PhysicsBody Component");
                    }
                }
                int ret = readPhysicsBody(pb);
                if (ret != 0) {
                    if ((ret & 1) != 0 && EntityUtils.isAdded(entity)) {
                        Log.warn("MessageReader.readPhysicsBody", "Body for Entity " + entityId + " has no UserData");
                    }
                }
            }
        }
        return 0;
    }

    private static final MassData tempMassData = new MassData();
    private final Vector2 tempVec = new Vector2();
    private final Transform tempTfm = new Transform();

    private int readPhysicsBody(PhysicsBodyComponent pb) {
        int r = 0;

        boolean zero;

        Transform tfm;
        Body body = null;
        if (pb == null || pb.body == null) {
//            Log.debug("MessageReader.readPhysicsBody", "Invalid PhysicsBodyComponent");
            tfm = tempTfm;
        }
        else {
            tfm = pb.body.getTransform();
            body = pb.body;
        }

        float t1 = packer.readFloat(tfm.vals[0], -3, GameProperties.MAP_WIDTH + 3, NetDriver.RES_POSITION);
        float t2 = packer.readFloat(tfm.vals[1], -3, GameProperties.MAP_HEIGHT + 3, NetDriver.RES_POSITION);
        float t3 = packer.readFloat(tfm.getRotation(), NetDriver.MIN_ORIENTATION, NetDriver.MAX_ORIENTATION, NetDriver.RES_ORIENTATION);

        Vector2 vel = body == null ? tempVec : body.getLinearVelocity();
        zero = packer.readInt(vel.x == 0 ? 0 : 1, 0, 1) == 0;
        float v1 = zero ? 0 : packer.readFloat(vel.x, -64, 64 - NetDriver.RES_VELOCITY, NetDriver.RES_VELOCITY);
        zero = packer.readInt(vel.y == 0 ? 0 : 1, 0, 1) == 0;
        float v2 = zero ? 0 : packer.readFloat(vel.y, -64, 64 - NetDriver.RES_VELOCITY, NetDriver.RES_VELOCITY);

        float angularVel = body == null ? 0 : body.getAngularVelocity();
        zero = packer.readInt(angularVel == 0 ? 0 : 1, 0, 1) == 0;
        float v3 = zero ? 0 : packer.readFloat(angularVel, -64, 64 - NetDriver.RES_VELOCITY, NetDriver.RES_VELOCITY);

        MassData md = body == null ? tempMassData : body.getMassData();
        zero = packer.readInt(md.mass == 0 ? 0 : 1, 0, 1) == 0;
        float m = zero ? 0 : packer.readFloat(md.mass, 0, 10, NetDriver.RES_MASS);
        float c1 = packer.readFloat(md.center.x, -3, 13 - NetDriver.RES_POSITION, NetDriver.RES_POSITION);
        float c2 = packer.readFloat(md.center.y, -3, 13 - NetDriver.RES_POSITION, NetDriver.RES_POSITION);
        zero = packer.readInt(md.I == 0 ? 0 : 1, 0, 1) == 0;
        float i = zero ? 0 : packer.readFloat(md.I, 0, 10, NetDriver.RES_MASS);

        if (packer.isReadMode() && body != null) {
            PhysicsSystem.PhysicsHistory physicsHistory = (PhysicsSystem.PhysicsHistory) body.getUserData();
            if (physicsHistory == null) {
                r = 1;
            }
            else {
                physicsHistory.updatePosition(t1, t2, tfm.vals[0], tfm.vals[1]);
                physicsHistory.updateVel(v1, v2, vel.x, vel.y);
                tempVec.set(t1 - tfm.vals[0], t2 - tfm.vals[1]);
                body.setTransform(t1, t2, t3);
                body.setLinearVelocity(v1, v2);
                body.setAngularVelocity(v3);
                tempMassData.mass = m;
                tempMassData.center.set(c1, c2);
                tempMassData.I = i;
                body.setMassData(tempMassData);
            }
        }

        return r;
    }

    private int readControlStates() {
        ControlSystem controlSystem = netDriver.getEngine().getSystem(ControlSystem.class);
        for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
            PlayerControlInfo playerControlInfo = controlSystem.getPlayerControlInfo(i);
            playerControlInfo.read(packer, netDriver.getEngine());
        }

        return 0;
    }

    private int readPlayerInfos(boolean refresh) {
        RefereeSystem refereeSystem = netDriver.getEngine().getSystem(RefereeSystem.class);

        if (packer.isWriteMode()) {
            packer.packInt(refereeSystem.getSortedPlayerInfos().size(), 0, NetDriver.MAX_CLIENTS);
            for (PlayerInfo playerInfo : refereeSystem.getSortedPlayerInfos()) {
                packer.packInt(playerInfo.playerId, 0, NetDriver.MAX_CLIENTS - 1);
                playerInfo.read(packer, netDriver.getEngine());
            }
        }
        else {
            int count = packer.unpackInt(0, NetDriver.MAX_CLIENTS);
            for (int i = 0; i < count; ++i) {
                int playerId = packer.unpackInt(0, NetDriver.MAX_CLIENTS - 1);
                PlayerInfo playerInfo = refereeSystem.getPlayerInfo(playerId);
                playerInfo.read(packer, netDriver.getEngine());
            }
            if (count != refereeSystem.getSortedPlayerInfos().size()) {
                refereeSystem.refreshSortedPlayerInfos();
            }
            if (refresh) {
                ScoreBoardRefreshEvent event = Pools.obtain(ScoreBoardRefreshEvent.class);
                netDriver.getEngine().getSystem(EventsSystem.class).queueEvent(event);
            }
        }

        return 0;
    }

    public static class MessageInfo {
        public int messageId = -1;
        public Message.MessageType type = null;
        public Array<Class<? extends NetDriver.NetworkEvent>> eventTypes = new Array<>();

        public void set(Message message) {
            messageId = message.messageId;
            type = message.getType();
            eventTypes.clear();
            eventTypes.addAll(eventTypes);
        }
    }
}
