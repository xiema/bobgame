package com.xam.bobgame.net;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.events.*;
import com.xam.bobgame.game.*;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.components.*;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.utils.BitPacker;

@SuppressWarnings("UnusedReturnValue")
public class MessageReader {

    private BitPacker packer = new BitPacker();
    private GameEngine engine;

    private MessageInfo[] messageInfos = new MessageInfo[NetDriver.MAX_MESSAGE_HISTORY];
    private int messageIdCounter = 0;

    private IntArray notUpdated = new IntArray(false, 4);

    public MessageReader() {
        for (int i = 0; i < messageInfos.length; ++i) {
            messageInfos[i] = new MessageInfo();
        }
    }

    public void setMessageInfo(Message message) {
        message.messageId = messageIdCounter;
        message.frameNum = engine.getCurrentFrame();
        messageInfos[messageIdCounter % messageInfos.length].set(message);
        messageIdCounter++;
    }

    public MessageInfo getMessageInfo(int messageId) {
        return messageInfos[messageId % messageInfos.length];
    }

    public int deserialize(Message message, Engine engine, int clientId) {
        this.engine = (GameEngine) engine;
        packer.setBuffer(message.getByteBuffer());
        packer.setReadMode();

        int entryCount = message.entryCount;

        switch (message.getType()) {
            case Update:
                if (this.engine.getMode() == GameEngine.Mode.Client && this.engine.getLastSnapshotFrame() == -1) {
                    Log.info("Got " + message + " Waiting for snapshot");
                    return -1;
                }
                // TODO: Server should only receive Event types
                while (entryCount-- > 0) {
                    Message.UpdateType updateType = Message.UpdateType.values()[packer.readInt(-1, 0, Message.UpdateType.values().length - 1)];
                    switch (updateType) {
                        case System:
                            readSystemUpdate();
                            break;
                        case Event:
                            readEvent(clientId);
                            break;
                    }
                    if (entryCount > 0) {
                        packer.skipToNextByte();
                    }
                }
                break;
            case Snapshot:
                readSystemSnapshot();
                ((GameEngine) engine).setLastSnapshotFrame();
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

        if (message.getByteBuffer().hasRemaining()) {
            Log.warn("Message has excess bytes: " + message);
        }

        if (notUpdated.notEmpty()) {
            for (int i = 0; i < notUpdated.size; ++i) {
                int entityId = notUpdated.get(i);
                Log.warn("Entity " + entityId + " was not updated");
            }
        }
        notUpdated.clear();

        return 0;
    }

    public int serialize(Message message, Engine engine, Message.MessageType type, ConnectionManager.ConnectionSlot connectionSlot) {
        this.engine = (GameEngine) engine;
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
                if (readSystemSnapshot() == -1) return -1;
                break;
            case Empty:
                break;
        }
        packer.padToNextByte();
        packer.flush(true);
        message.setLength(packer.getTotalBytes());
        message.entryCount = 1;

        return 0;
    }

    public int serializeInput(Message message, Engine engine, PlayerControlEvent event) {
        this.engine = (GameEngine) engine;
        packer.setBuffer(message.getByteBuffer());
        packer.setWriteMode();

        message.setType(Message.MessageType.Input);
        setMessageInfo(message);
        packer.packInt(NetDriver.getNetworkEventIndex(event.getClass()), 0, NetDriver.networkEventClasses.length - 1);
        event.read(packer, engine);
        packer.padToNextByte();
        packer.flush(true);
        message.setLength(packer.getTotalBytes());
        message.entryCount = 1;

        return 0;
    }

    public int serializeEvent(Message message, Engine engine, NetDriver.NetworkEvent event) {
        this.engine = (GameEngine) engine;
        packer.setBuffer(message.getByteBuffer());
        packer.setWriteMode();

        message.setType(Message.MessageType.Update);
        setMessageInfo(message);
        packer.packInt(Message.UpdateType.Event.getValue(), 0, Message.UpdateType.values().length - 1);
        message.eventTypes.add(event.getClass());
        int typeIndex = NetDriver.getNetworkEventIndex(event.getClass());
        if (typeIndex == -1) {
            Log.error("Unknown typeIndex for " + event.getClass());
            return -1;
        }
        packer.packInt(typeIndex, 0, NetDriver.networkEventClasses.length - 1);
        event.read(packer, engine);
        packer.padToNextByte();
        packer.flush(true);
        message.setLength(packer.getTotalBytes());
        message.entryCount = 1;

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
        event.read(packer, engine);

        if (event instanceof EntityDespawnedEvent) {
            EntityDespawnedEvent despawnedEvent = (EntityDespawnedEvent) event;
            notUpdated.removeValue(despawnedEvent.entityId);
        }

        if (packer.isReadMode()) {
            event.clientId = clientId;
            engine.getSystem(EventsSystem.class).queueEvent(event);
        }

        return 0;
    }

    private int readSystemUpdate() {
        readPhysicsBodies();
        readControlStates();
        readPlayerInfos(false);

        return 0;
    }

    private final EntityCreatedEvent entityCreator = Pools.obtain(EntityCreatedEvent.class);

    private int readSystemSnapshot() {
        ImmutableArray<Entity> entities = engine.getEntities();
        int cnt = packer.readInt(entities.size(), 0, NetDriver.MAX_ENTITY_ID);
        int i = 0;
        while (cnt-- > 0) {
            if (packer.isWriteMode()) {
                Entity entity = entities.get(i++);
                entityCreator.entityId = EntityUtils.getId(entity);
            }
            // TODO: include entity position
            entityCreator.read(packer, engine);
        }

        readPlayerInfos(true);

        return 0;
    }

    private int readPhysicsBodies() {
        IntMap<Entity> entityMap = engine.getEntityMap();
        IntArray sortedEntityIds = engine.getSortedEntityIds();

        if (packer.isWriteMode()) {
            packer.packInt(sortedEntityIds.size, 0, NetDriver.MAX_ENTITY_ID);
            for (int i = 0; i < sortedEntityIds.size; ++i) {
                int entityId = sortedEntityIds.get(i);
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
                while (j < sortedEntityIds.size && entityId < sortedEntityIds.get(j)) {
                    Log.warn("Received update for nonexistent entity " + entityId);
                    j++;
                }
                while (j < sortedEntityIds.size && entityId > sortedEntityIds.get(j)) {
                    Log.debug("Entity " + sortedEntityIds.get(j) + " skipped during update");
                    notUpdated.add(sortedEntityIds.get(j));
                    j++;
                }
                if (j < sortedEntityIds.size && entityId == sortedEntityIds.get(j)) j++;
                Entity entity = entityMap.get(entityId, null);
                PhysicsBodyComponent pb = null;
                if (entity != null) {
                    pb = ComponentMappers.physicsBody.get(entity);
                    if (pb == null) {
                        Log.warn("Entity " + entityId + "has no PhysicsBody Component");
                    }
                }
                int ret = readPhysicsBody(pb);
                if (ret == -1) {
                    Log.warn("Error encountered while updating entity " + entityId);
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
                Log.warn("MessageReader.readPhysicsBody", "Body has no UserData");
                r = -1;
            }
            else {
                if (physicsHistory.posXError.isInit()) {
                    physicsHistory.posXError.update(t1 - (tfm.vals[0] + physicsHistory.posXError.getAverage()));
                    physicsHistory.posYError.update(t2 - (tfm.vals[1] + physicsHistory.posYError.getAverage()));
                }
                else {
                    physicsHistory.posXError.update(0);
                    physicsHistory.posYError.update(0);
                }
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
        ControlSystem controlSystem = engine.getSystem(ControlSystem.class);
        for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
            PlayerControlInfo playerControlInfo = controlSystem.getPlayerControlInfo(i);
            playerControlInfo.read(packer, engine);
        }

        return 0;
    }

    private int readPlayerInfos(boolean refresh) {
        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);

        for (int i = 0; i < NetDriver.MAX_CLIENTS; ++i) {
            PlayerInfo playerInfo = refereeSystem.getPlayerInfo(i);
            playerInfo.read(packer, engine);
        }

        if (packer.isReadMode() && refresh) {
            ScoreBoardRefreshEvent event = Pools.obtain(ScoreBoardRefreshEvent.class);
            engine.getSystem(EventsSystem.class).queueEvent(event);
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
