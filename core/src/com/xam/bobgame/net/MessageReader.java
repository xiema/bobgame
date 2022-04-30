package com.xam.bobgame.net;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.Pools;
import com.xam.bobgame.GameDirector;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.components.IdentityComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.events.EventsSystem;
import com.xam.bobgame.game.ControlSystem;
import com.xam.bobgame.game.ShapeDef;
import com.xam.bobgame.graphics.TextureDef;
import com.xam.bobgame.utils.BitPacker;

@SuppressWarnings("UnusedReturnValue")
public class MessageReader {

    private BitPacker builder = new BitPacker();
    private Engine engine;

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

    public void setMessageInfo(Message message) {
        message.messageId = messageIdCounter;
        messageInfos[messageIdCounter % messageInfos.length].set(message);
        messageIdCounter++;
    }

    public MessageInfo getMessageInfo(int messageId) {
        return messageInfos[messageId % NetDriver.MAX_MESSAGE_HISTORY];
    }

    public int deserialize(Message message, Engine engine) {
        this.engine = engine;
        builder.setBuffer(message.getByteBuffer());
        send = false;

        switch (message.getType()) {
            case Update:
                if (((GameEngine) engine).getLastSnapshotFrame() == -1) return -1;
                readSystemUpdate();
                readFooter();
                break;
            case Snapshot:
                readSystemSnapshot();
                if (readPlayerId(null) == -1) return -1;
                readFooter();
                ((GameEngine) engine).setLastSnapshotFrame();
                break;
            case Empty:
                // empty
                readFooter();
                break;
            default:
                return -1;
        }

        return 0;
    }

    public int serialize(Message message, Engine engine, Message.MessageType type, ConnectionManager.ConnectionSlot connectionSlot) {
        this.engine = engine;
        builder.setBuffer(message.getByteBuffer());
        send = true;

        setMessageInfo(message);
        message.setType(type);
        switch (type) {
            case Update:
                if (readSystemUpdate() == -1) return -1;
                break;
            case Snapshot:
                if (readSystemSnapshot() == -1) return -1;
                if (readPlayerId(connectionSlot) == -1) return -1;
                break;
            case Empty:
                break;
        }
        readFooter();
        builder.flush(true);
        message.setLength(builder.getTotalBytes());

        return 0;
    }

    public int serializeEvent(Message message, NetDriver.NetworkEvent event) {
        builder.setBuffer(message.getByteBuffer());
        send = true;

        setMessageInfo(message);
        message.setType(Message.MessageType.Event);
        builder.packInt(NetDriver.getNetworkEventIndex(event.getClass()), 0, NetDriver.networkEventClasses.length - 1);
        event.read(builder, true);
        builder.flush(true);
        message.setLength(builder.getTotalBytes());

        return 0;
    }

    public int readEvent(Message message, Engine engine, int clientId) {
        this.engine = engine;
        builder.setBuffer(message.getByteBuffer());
        send = false;

        int type = builder.unpackInt(0, NetDriver.networkEventClasses.length - 1);
        //noinspection unchecked
        NetDriver.NetworkEvent event = Pools.obtain((Class<? extends NetDriver.NetworkEvent>) NetDriver.networkEventClasses[type]);
//        Log.info("clientID=0 playerId=" + netDriver.getConnectionManager().getPlayerId(0));
        event.read(builder, false);

        if (!send) {
            engine.getSystem(EventsSystem.class).queueEvent(event);
        }

        return 0;
    }

    private int readPlayerId(ConnectionManager.ConnectionSlot connectionSlot) {
        if (send) {
            builder.packInt(connectionSlot.playerId, 0, 32);
        }
        else {
            engine.getSystem(GameDirector.class).setLocalPlayerId(builder.unpackInt(0, 32));
        }
        return 0;
    }

    private int readFooter() {
        readByte((byte) 0xFF);
        return 0;
    }

    private int readSystemUpdate() {
        ImmutableArray<Entity> entities = engine.getSystem(GameDirector.class).getEntities();
        if (entities.size() == 0) return 0;

        int cnt = readInt(entities.size(), 0, 255);
        int i = 0;
        while (cnt-- > 0) {
            Entity entity = entities.get(i);
            int id1 = EntityUtils.getId(entity);
            int id2 = readInt(id1, 0, 255);
            while (id1 != id2) {
                i = (i + 1) % entities.size();
                entity = entities.get(i);
                id1 = EntityUtils.getId(entity);
            }
            readPhysicsBody(ComponentMappers.physicsBody.get(entity));
            i = (i + 1) % entities.size();
        }

        return 0;
    }

    private int readSystemSnapshot() {
        ImmutableArray<Entity> entities = engine.getSystem(GameDirector.class).getEntities();
        int cnt = readInt(entities.size(), 0, 255);
        int i = 0, j;
        while (cnt-- > 0) {
            Entity entity;
            if (send) {
                entity = entities.get(i++);
                readEntity(entity);
            }
            else {
                entity = engine.createEntity();
                readEntity(entity);
                engine.addEntity(entity);
            }
        }

        ControlSystem controlSystem = engine.getSystem(ControlSystem.class);
        if (!send) controlSystem.clearRegistry();

        for (i = 0; i < 32; ++i) {
            IntArray entityIds = controlSystem.getControlledEntityIds(i);
            int b = readInt(entityIds.size == 0 ? 0 : 1, 0, 1);
            if (b == 0) continue;
            cnt = readInt(entityIds.size, 0, 255);
            int entityId;
            for (j = 0; j < cnt; ++j) {
                entityId = readInt(entityIds.size == 0 ? -1 : entityIds.get(j), 0, 255);
                if (!send) controlSystem.registerEntity(entityId, i);
            }
        }

        return 0;
    }

    private static final MassData tempMassData = new MassData();

    private int readPhysicsBody(PhysicsBodyComponent pb) {
        boolean zero;

        Transform tfm = pb.body.getTransform();

        float t1 = readFloat(tfm.vals[0], -3, 13, NetDriver.RES_POSITION);
        float t2 = readFloat(tfm.vals[1], -3, 13, NetDriver.RES_POSITION);
        float t3 = readFloat(tfm.getRotation(), 0, NetDriver.MAX_ORIENTATION, NetDriver.RES_ORIENTATION);

        Vector2 vel = pb.body.getLinearVelocity();
        zero = readInt(vel.x == 0 ? 0 : 1, 0, 1) == 0;
        float v1 = zero ? 0 : readFloat(vel.x, -1000, 1000, NetDriver.RES_VELOCITY);
        zero = readInt(vel.y == 0 ? 0 : 1, 0, 1) == 0;
        float v2 = zero ? 0 : readFloat(vel.y, -1000, 1000, NetDriver.RES_VELOCITY);

        float angularVel = pb.body.getAngularVelocity();
        zero = readInt(angularVel == 0 ? 0 : 1, 0, 1) == 0;
        float v3 = zero ? 0 : readFloat(angularVel, -1000, 1000, NetDriver.RES_VELOCITY);

        MassData md = pb.body.getMassData();
        zero = readInt(md.mass == 0 ? 0 : 1, 0, 1) == 0;
        float m = zero ? 0 : readFloat(md.mass, 0, 10, NetDriver.RES_MASS);
        float c1 = readFloat(md.center.x, -3, 13, NetDriver.RES_POSITION);
        float c2 = readFloat(md.center.y, -3, 13, NetDriver.RES_POSITION);
        zero = readInt(md.I == 0 ? 0 : 1, 0, 1) == 0;
        float i = zero ? 0 : readFloat(md.I, 0, 10, NetDriver.RES_MASS);

//        Log.info("m=" + m + " cx=" + c1 + " cy=" + c2 + " i=" + i);

        if (!send) {
            pb.body.setTransform(t1, t2, t3);
            pb.body.setLinearVelocity(v1, v2);
            pb.body.setAngularVelocity(v3);
            tempMassData.mass = m;
            tempMassData.center.set(c1, c2);
            tempMassData.I = i;
            pb.body.setMassData(tempMassData);
        }

        return 0;
    }

    private int readEntity(Entity entity) {

        PhysicsBodyComponent pb;
        GraphicsComponent graphics;
        IdentityComponent iden;

        if (!send) {
            pb = engine.createComponent(PhysicsBodyComponent.class);
            pb.bodyDef = new BodyDef();
            pb.fixtureDef = new FixtureDef();
            pb.shapeDef = new ShapeDef();
            graphics = engine.createComponent(GraphicsComponent.class);
            graphics.textureDef = new TextureDef();
            iden = engine.createComponent(IdentityComponent.class);
        }
        else {
            pb = ComponentMappers.physicsBody.get(entity);
            graphics = ComponentMappers.graphics.get(entity);
            iden = ComponentMappers.identity.get(entity);
        }

        iden.id = readInt(iden.id, 0, 255);

        pb.bodyDef.type = BodyDef.BodyType.values()[readInt(pb.bodyDef.type.getValue(), 0, BodyDef.BodyType.values().length)];
        pb.bodyDef.position.x = readFloat(pb.bodyDef.position.x, -3, 13, NetDriver.RES_POSITION);
        pb.bodyDef.position.y = readFloat(pb.bodyDef.position.y, -3, 13, NetDriver.RES_POSITION);
        pb.bodyDef.linearDamping = readFloat(pb.bodyDef.linearDamping, 0, 1, NetDriver.RES_MASS);
        pb.shapeDef.type = ShapeDef.ShapeType.values()[readInt(pb.shapeDef.type.getValue(), 0, ShapeDef.ShapeType.values().length)];
        pb.shapeDef.shapeVal1 = readFloat(pb.shapeDef.shapeVal1, 0, 16, NetDriver.RES_POSITION);
        pb.fixtureDef.density = readFloat(pb.fixtureDef.density, 0, 16, NetDriver.RES_MASS);
        pb.fixtureDef.friction = readFloat(pb.fixtureDef.friction, 0, 16, NetDriver.RES_MASS);
        pb.fixtureDef.restitution = readFloat(pb.fixtureDef.restitution, 0, 16, NetDriver.RES_MASS);

        graphics.textureDef.type = TextureDef.TextureType.values()[readInt(graphics.textureDef.type.getValue(), 0, TextureDef.TextureType.values().length)];
        graphics.textureDef.wh = readInt(graphics.textureDef.wh, 0, 128);
        graphics.textureDef.textureVal1 = readInt(graphics.textureDef.textureVal1, 0, 128);
        float r = readFloat(graphics.textureDef.color.r, 0, 1, NetDriver.RES_COLOR);
        float g = readFloat(graphics.textureDef.color.g, 0, 1, NetDriver.RES_COLOR);
        float b = readFloat(graphics.textureDef.color.b, 0, 1, NetDriver.RES_COLOR);
        float a = readFloat(graphics.textureDef.color.a, 0, 1, NetDriver.RES_COLOR);
        float w = readFloat(graphics.spriteActor.getSprite().getWidth(), 0, 16, NetDriver.RES_POSITION);
        float h = readFloat(graphics.spriteActor.getSprite().getHeight(), 0, 16, NetDriver.RES_POSITION);

        if (!send) {
            graphics.textureDef.color.set(r, g, b, a);
            Sprite sprite = graphics.spriteActor.getSprite();
            sprite.setRegion(new TextureRegion(graphics.textureDef.createTexture()));
            sprite.setSize(w, h);
            sprite.setOriginCenter();

            entity.add(iden);
            entity.add(pb);
            entity.add(graphics);
        }

        return 0;
    }

    public static class MessageInfo {
        public int messageId;
        public Message.MessageType type;

        public void set(Message message) {
            messageId = message.messageId;
            type = message.getType();
        }
    }
}
