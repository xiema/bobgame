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
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.components.*;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.events.EntityCreatedEvent;
import com.xam.bobgame.events.EventsSystem;
import com.xam.bobgame.events.PlayerControlEvent;
import com.xam.bobgame.events.ScoreBoardRefreshEvent;
import com.xam.bobgame.game.ControlSystem;
import com.xam.bobgame.game.PhysicsSystem;
import com.xam.bobgame.utils.BitPacker;

@SuppressWarnings("UnusedReturnValue")
public class MessageReader {

    private BitPacker builder = new BitPacker();
    private GameEngine engine;

    private MessageInfo[] messageInfos = new MessageInfo[NetDriver.MAX_MESSAGE_HISTORY];
    private int messageIdCounter = 0;

    public MessageReader() {
        for (int i = 0; i < messageInfos.length; ++i) {
            messageInfos[i] = new MessageInfo();
        }
    }

    private boolean send = false;

    private int readInt(int i, int min, int max) {
        if (send) {
            builder.packInt(i, min, max);
            return i;
        }
        else {
            return builder.unpackInt(min, max);
        }
    }

    private float readFloat(float f) {
        if (send) {
            builder.packFloat(f);
            return f;
        }
        else {
            return builder.unpackFloat();
        }
    }

    private float readFloat(float f, float min, float max, float res) {
        if (send) {
            builder.packFloat(f, min, max, res);
            return f;
        }
        else {
            return builder.unpackFloat(min, max, res);
        }
    }

    private byte readByte(byte b) {
        if (send) {
            builder.packByte(b);
            return b;
        }
        else {
            return builder.unpackByte();
        }
    }

    private boolean readBoolean(boolean b) {
        if (send) {
            builder.packInt(b ? 1 : 0, 0, 1);
            return b;
        }
        else {
            return builder.unpackInt(0, 1) == 1;
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
        builder.setBuffer(message.getByteBuffer());
        send = false;

        int entryCount = message.entryCount;

        switch (message.getType()) {
            case Update:
                if (engine.getSystem(NetDriver.class).getMode() == NetDriver.Mode.Client && this.engine.getLastSnapshotFrame() == -1) {
                    Log.info("Got " + message + " Waiting for snapshot");
                    return -1;
                }
                // TODO: Server should only receive Event types
                while (entryCount-- > 0) {
                    Message.UpdateType updateType = Message.UpdateType.values()[readInt(-1, 0, Message.UpdateType.values().length - 1)];
                    switch (updateType) {
                        case System:
                            readSystemUpdate();
                            break;
                        case Event:
                            readEvent(clientId);
                            break;
                    }
                    if (entryCount > 0) {
                        builder.skipToNextByte();
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
            Log.warn("Message has excess bytes");
        }

        return 0;
    }

    public int serialize(Message message, Engine engine, Message.MessageType type, ConnectionManager.ConnectionSlot connectionSlot) {
        this.engine = (GameEngine) engine;
        builder.setBuffer(message.getByteBuffer());
        send = true;

        message.setType(type);
        setMessageInfo(message);
        switch (type) {
            case Update:
                builder.packInt(Message.UpdateType.System.getValue(), 0, Message.UpdateType.values().length - 1);
                if (readSystemUpdate() == -1) return -1;
                break;
            case Snapshot:
                if (readSystemSnapshot() == -1) return -1;
//                if (readPlayerId(connectionSlot) == -1) return -1;
                break;
            case Empty:
                break;
        }
        builder.padToNextByte();
        builder.flush(true);
        message.setLength(builder.getTotalBytes());
        message.entryCount = 1;

        return 0;
    }

    public int serializeInput(Message message, Engine engine, PlayerControlEvent event) {
        this.engine = (GameEngine) engine;
        builder.setBuffer(message.getByteBuffer());
        send = true;

        message.setType(Message.MessageType.Input);
        setMessageInfo(message);
        builder.packInt(NetDriver.getNetworkEventIndex(event.getClass()), 0, NetDriver.networkEventClasses.length - 1);
        event.read(builder, engine, true);
        builder.padToNextByte();
        builder.flush(true);
        message.setLength(builder.getTotalBytes());
        message.entryCount = 1;

        return 0;
    }

    public int serializeEvent(Message message, Engine engine, NetDriver.NetworkEvent event) {
        this.engine = (GameEngine) engine;
        builder.setBuffer(message.getByteBuffer());
        send = true;

        message.setType(Message.MessageType.Update);
        setMessageInfo(message);
        builder.packInt(Message.UpdateType.Event.getValue(), 0, Message.UpdateType.values().length - 1);
        message.eventTypes.add(event.getClass());
        int typeIndex = NetDriver.getNetworkEventIndex(event.getClass());
        if (typeIndex == -1) {
            Log.error("Unknown typeIndex for " + event.getClass());
            return -1;
        }
        builder.packInt(typeIndex, 0, NetDriver.networkEventClasses.length - 1);
        event.read(builder, engine, true);
        builder.padToNextByte();
        builder.flush(true);
        message.setLength(builder.getTotalBytes());
        message.entryCount = 1;

        return 0;
    }

    private int readEvent(int clientId) {
        int type = builder.unpackInt(0, NetDriver.networkEventClasses.length - 1);
        if (type >= NetDriver.networkEventClasses.length) {
            Log.error("MessageReader", "Invalid network event id " + type + " from client " + clientId);
            return -1;
        }
        //noinspection unchecked
        NetDriver.NetworkEvent event = Pools.obtain((Class<? extends NetDriver.NetworkEvent>) NetDriver.networkEventClasses[type]);
        event.read(builder, engine, false);

        if (!send) {
            event.clientId = clientId;
            engine.getSystem(EventsSystem.class).queueEvent(event);
        }

        return 0;
    }

//    public int readEvent(Message message, Engine engine, int clientId) {
//        this.engine = engine;
//        builder.setBuffer(message.getByteBuffer());
//        send = false;
//
//        int type = builder.unpackInt(0, NetDriver.networkEventClasses.length - 1);
//        //noinspection unchecked
//        NetDriver.NetworkEvent event = Pools.obtain((Class<? extends NetDriver.NetworkEvent>) NetDriver.networkEventClasses[type]);
////        Log.info("clientID=0 playerId=" + netDriver.getConnectionManager().getPlayerId(0));
//        event.read(builder, false);
//
//        if (!send) {
//            engine.getSystem(EventsSystem.class).queueEvent(event);
//        }
//
//        return 0;
//    }

    private int readPlayerId(ConnectionManager.ConnectionSlot connectionSlot) {
        if (send) {
            builder.packInt(connectionSlot.playerId, 0, 32);
        }
        else {
            engine.getSystem(RefereeSystem.class).setLocalPlayerId(builder.unpackInt(0, 32));
        }
        return 0;
    }

    private int readSystemUpdate() {
        IntMap<Entity> entityMap = engine.getEntityMap();
        IntArray sortedEntityIds = engine.getSortedEntityIds();

        int cnt = readInt(sortedEntityIds.size, 0, NetDriver.MAX_ENTITY_ID);
        for (int i = 0; i < cnt; ++i) {
            int entityId = readInt(send ? sortedEntityIds.get(i) : -1, 0, NetDriver.MAX_ENTITY_ID);
            Entity entity = entityMap.get(entityId, null);
            if (entity == null) {
                Log.debug("Unable to update state of entity " + entityId);
            }
            readPhysicsBody(entity == null ? null : ComponentMappers.physicsBody.get(entity));
        }

        readControlStates();
        readPlayerScores(false);

        return 0;
    }

    private final EntityCreatedEvent entityCreator = Pools.obtain(EntityCreatedEvent.class);

    private int readSystemSnapshot() {
        ImmutableArray<Entity> entities = engine.getEntities();
        int cnt = readInt(entities.size(), 0, NetDriver.MAX_ENTITY_ID);
        int i = 0;
        while (cnt-- > 0) {
            if (send) {
                Entity entity = entities.get(i++);
                entityCreator.entityId = EntityUtils.getId(entity);
            }
            // TODO: include entity position
            entityCreator.read(builder, engine, send);
        }

        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
        int[] playerControlMap = refereeSystem.getPlayerControlMap();

        for (i = 0; i < 32; ++i) {
            playerControlMap[i] = readInt(playerControlMap[i], -1, 254);
        }

        readPlayerScores(true);

        return 0;
    }

    private static final MassData tempMassData = new MassData();
    private final Vector2 tempVec = new Vector2();
    private final Transform tempTfm = new Transform();

    private int readPhysicsBody(PhysicsBodyComponent pb) {
        boolean zero;

        Transform tfm;
        if (pb == null || pb.body == null) {
            Log.warn("MessageReader.readPhysicsBody", "Invalid PhysicsBodyComponent");
            tfm = tempTfm;
        }
        else {
            tfm = pb.body.getTransform();
        }

        float t1 = readFloat(tfm.vals[0], -3, GameProperties.MAP_WIDTH + 3, NetDriver.RES_POSITION);
        float t2 = readFloat(tfm.vals[1], -3, GameProperties.MAP_HEIGHT + 3, NetDriver.RES_POSITION);
        float t3 = readFloat(tfm.getRotation(), NetDriver.MIN_ORIENTATION, NetDriver.MAX_ORIENTATION, NetDriver.RES_ORIENTATION);

        Vector2 vel = pb == null ? tempVec : pb.body.getLinearVelocity();
        zero = readInt(vel.x == 0 ? 0 : 1, 0, 1) == 0;
        float v1 = zero ? 0 : readFloat(vel.x, -64, 64 - NetDriver.RES_VELOCITY, NetDriver.RES_VELOCITY);
        zero = readInt(vel.y == 0 ? 0 : 1, 0, 1) == 0;
        float v2 = zero ? 0 : readFloat(vel.y, -64, 64 - NetDriver.RES_VELOCITY, NetDriver.RES_VELOCITY);

        float angularVel = pb == null ? 0 : pb.body.getAngularVelocity();
        zero = readInt(angularVel == 0 ? 0 : 1, 0, 1) == 0;
        float v3 = zero ? 0 : readFloat(angularVel, -64, 64 - NetDriver.RES_VELOCITY, NetDriver.RES_VELOCITY);

        MassData md = pb == null ? tempMassData : pb.body.getMassData();
        zero = readInt(md.mass == 0 ? 0 : 1, 0, 1) == 0;
        float m = zero ? 0 : readFloat(md.mass, 0, 10, NetDriver.RES_MASS);
        float c1 = readFloat(md.center.x, -3, 13 - NetDriver.RES_POSITION, NetDriver.RES_POSITION);
        float c2 = readFloat(md.center.y, -3, 13 - NetDriver.RES_POSITION, NetDriver.RES_POSITION);
        zero = readInt(md.I == 0 ? 0 : 1, 0, 1) == 0;
        float i = zero ? 0 : readFloat(md.I, 0, 10, NetDriver.RES_MASS);

        if (!send && pb != null) {
            PhysicsSystem.PhysicsHistory physicsHistory = (PhysicsSystem.PhysicsHistory) pb.body.getUserData();
            if (physicsHistory == null) {
                Log.warn("MessageReader.readPhysicsBody", "Body has no UserData");
            }
            else {
                physicsHistory.posXError.update(t1 - (tfm.vals[0] + physicsHistory.posXError.getAverage()));
                physicsHistory.posYError.update(t2 - (tfm.vals[1] + physicsHistory.posYError.getAverage()));
                tempVec.set(t1 - tfm.vals[0], t2 - tfm.vals[1]);
                pb.body.setTransform(t1, t2, t3);
                pb.body.setLinearVelocity(v1, v2);
                pb.body.setAngularVelocity(v3);
                tempMassData.mass = m;
                tempMassData.center.set(c1, c2);
                tempMassData.I = i;
                pb.body.setMassData(tempMassData);
            }
        }

        return 0;
    }

    private int readControlStates() {
        ControlSystem controlSystem = engine.getSystem(ControlSystem.class);
        boolean[] buttonStates = controlSystem.getButtonStates();
        float[] buttonHoldDurations = controlSystem.getButtonHoldDurations();
        for (int i = 0; i < buttonStates.length; ++i) {
            buttonStates[i] = readInt(buttonStates[i] ? 1 : 0, 0, 1) != 0;
            buttonHoldDurations[i] = readFloat(buttonHoldDurations[i], -NetDriver.RES_HOLD_DURATION, GameProperties.CHARGE_DURATION_2, NetDriver.RES_HOLD_DURATION);
        }

        return 0;
    }

    private int readPlayerScores(boolean refresh) {
        RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
        boolean[] playerExists = refereeSystem.getPlayerExists();
        int[] playerControlMap = refereeSystem.getPlayerControlMap();
        int[] playerScores = refereeSystem.getPlayerScores();

        for (int i = 0; i < playerControlMap.length; ++i) {
            playerExists[i] = readInt(playerExists[i] ? 1 : 0, 0, 1) == 1;
            playerControlMap[i] = readInt(playerControlMap[i], -1, NetDriver.MAX_ENTITY_ID);
            playerScores[i] = readInt(playerScores[i], NetDriver.MIN_SCORE, NetDriver.MAX_SCORE);
        }

        if (!send && refresh) {
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
