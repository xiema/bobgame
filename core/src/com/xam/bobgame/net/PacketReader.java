package com.xam.bobgame.net;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameDirector;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.components.IdentityComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentFactory;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.game.ShapeDef;
import com.xam.bobgame.graphics.TextureDef;

public class PacketReader {
    public static final float RES_POSITION = 1e-6f;
    public static final float RES_ORIENTATION = 1e-6f;
    public static final float RES_VELOCITY = 1e-6f;
    public static final float RES_MASS = 1e-6f;
    public static final float RES_COLOR = 1e-6f;
    public static final float MAX_ORIENTATION = 3.14159f;

    Packet.PacketBuilder builder = new Packet.PacketBuilder();
    Engine engine;

    private boolean write = false;

    private int readInt(int i, int min, int max) {
        if (write) {
            builder.packInt(i, min, max);
            return i;
        }
        else {
            return builder.unpackInt(min, max);
        }
    }

    private float readFloat(float f, float min, float max, float res) {
        if (write) {
            builder.packFloat(f, min, max, res);
            return f;
        }
        else {
            return builder.unpackFloat(min, max, res);
        }
    }

    private byte readByte(byte b) {
        if (write) {
            builder.packByte(b);
            return b;
        }
        else {
            return builder.unpackByte();
        }
    }

    public int syncEngine(Packet packet, Engine engine) {
        this.engine = engine;
        builder.setPacket(packet);
        write = false;

        switch (builder.unpackByte()) {
            case 1:
                return readSystemUpdate();
            case 2:
                return readSystemSnapshot();
            default:
                return -1;
        }
    }

    public int serialize(Packet packet, Engine engine, int option) {
        this.engine = engine;
        builder.setPacket(packet);
        write = true;

        builder.packByte((byte) option);
        switch (option) {
            case 1:
                if (readSystemUpdate() == -1) return -1;
                break;
            case 2:
                if (readSystemSnapshot() == -1) return -1;
                break;
        }
        builder.flush(true);

        return 0;
    }

    private void zeroBytes(byte[] bytes) {
        int i = bytes.length;
        while (i-- > 0) {
            bytes[i] = 0;
        }
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
        int i = 0;
        while (cnt-- > 0) {
            Entity entity;
            if (write) {
                entity = entities.get(i++);
                readEntity(entity);
            }
            else {
                entity = engine.createEntity();
                readEntity(entity);
                engine.addEntity(entity);
            }
        }

        return 0;
    }

    private static MassData tempMassData = new MassData();

    private int readPhysicsBody(PhysicsBodyComponent pb) {
        Transform tfm = pb.body.getTransform();

        float t1 = readFloat(tfm.vals[0], -3, 13, RES_POSITION);
        float t2 = readFloat(tfm.vals[1], -3, 13, RES_POSITION);
        float t3 = readFloat(tfm.getRotation(), 0, MAX_ORIENTATION, RES_POSITION);

        Vector2 vel = pb.body.getLinearVelocity();
        float v1 = readFloat(vel.x, -1000, 1000, RES_VELOCITY);
        float v2 = readFloat(vel.y, -1000, 1000, RES_VELOCITY);
        float v3 = readFloat(pb.body.getAngularVelocity(), -1000, 1000, RES_VELOCITY);

        MassData md = pb.body.getMassData();
        float m = readFloat(md.mass, 0, 10, RES_MASS);
        float c1 = readFloat(md.center.x, -3, 13, RES_POSITION);
        float c2 = readFloat(md.center.y, -3, 13, RES_POSITION);
        float i = readFloat(md.I, 0, 10, RES_MASS);

//        Log.info("m=" + m + " cx=" + c1 + " cy=" + c2 + " i=" + i);

        if (!write) {
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

        if (!write) {
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

        pb.bodyDef.type = BodyDef.BodyType.values()[readInt(pb.bodyDef.type.getValue(), 0, BodyDef.BodyType.values().length + 1)];
        pb.bodyDef.position.x = readFloat(pb.bodyDef.position.x, -3, 13, RES_POSITION);
        pb.bodyDef.position.y = readFloat(pb.bodyDef.position.y, -3, 13, RES_POSITION);
        pb.shapeDef.type = ShapeDef.ShapeType.values()[readInt(pb.shapeDef.type.getValue(), 0, ShapeDef.ShapeType.values().length + 1)];
        pb.shapeDef.shapeVal1 = readFloat(pb.shapeDef.shapeVal1, 0, 16, RES_POSITION);
        pb.fixtureDef.density = readFloat(pb.fixtureDef.density, 0, 16, RES_MASS);
        pb.fixtureDef.friction = readFloat(pb.fixtureDef.friction, 0, 16, RES_MASS);
        pb.fixtureDef.restitution = readFloat(pb.fixtureDef.restitution, 0, 16, RES_MASS);

        graphics.textureDef.type = TextureDef.TextureType.values()[readInt(graphics.textureDef.type.getValue(), 0, TextureDef.TextureType.values().length + 1)];
        graphics.textureDef.wh = readInt(graphics.textureDef.wh, 0, 512);
        graphics.textureDef.textureVal1 = readInt(graphics.textureDef.textureVal1, 0, 512);
        float r = readFloat(graphics.textureDef.color.r, 0, 1, RES_COLOR);
        float g = readFloat(graphics.textureDef.color.g, 0, 1, RES_COLOR);
        float b = readFloat(graphics.textureDef.color.b, 0, 1, RES_COLOR);
        float a = readFloat(graphics.textureDef.color.a, 0, 1, RES_COLOR);
        float w = readFloat(graphics.spriteActor.getSprite().getWidth(), 0, 16, RES_POSITION);
        float h = readFloat(graphics.spriteActor.getSprite().getHeight(), 0, 16, RES_POSITION);

        if (!write) {
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

    private enum State {
        Start() {
            @Override
            int deserialize(PacketReader ps) {
                return Command.deserialize(ps);
            }
            @Override
            int serialize(PacketReader ps) {
                return Command.serialize(ps) + ps.builder.flush(true);
            }
        },
        Command() {
            @Override
            int deserialize(PacketReader ps) {
                while (ps.builder.hasRemaining()) {
                    byte b = ps.builder.unpackByte();
                    int i = -1;
                    switch (b) {
                        case 1:
                            i = SystemUpdate.deserialize(ps);
                            break;
                        case 2:
                            i = SystemSnapshot.deserialize(ps);
                            break;
                    }
                    if (i == -1) return -1;
                }

                return 0;
            }
            @Override
            int serialize(PacketReader ps) {
                int i = 0, j = -1;
//                i += ps.builder.packByte((byte) ps.option);
//                switch(ps.option) {
//                    case 1:
//                        j = SystemUpdate.serialize(ps);
//                        break;
//                    case 2:
//                        j = SystemSnapshot.serialize(ps);
//                        break;
//                }
                if (j == -1) return -1;
                i += j;
                return i;
            }
        },
        SystemUpdate() {
            @Override
            int deserialize(PacketReader ps) {
                ImmutableArray<Entity> entities = ps.engine.getSystem(GameDirector.class).getEntities();
                int i = 0;
//                int cnt = ps.byteBuffer.getInt();
                int cnt = ps.builder.unpackInt(0, 255);
                while (cnt-- > 0) {
//                    int id = ps.byteBuffer.getInt();
                    int id = ps.builder.unpackInt(0, 255);
                    while (EntityUtils.getId(entities.get(i)) != id) {
                        i = (i + 1) % entities.size();
                    }
                    Entity entity = entities.get(i);
                    PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
                    pb.body.setTransform(ps.builder.unpackFloat(-1,11, RES_POSITION),
                            ps.builder.unpackFloat(-1,11, RES_POSITION),
                            ps.builder.unpackFloat(0,MAX_ORIENTATION, RES_ORIENTATION));

                    pb.body.setLinearVelocity(ps.builder.unpackFloat(-1000, 1000, RES_VELOCITY),
                            ps.builder.unpackFloat(-1000, 1000, RES_VELOCITY));
                    pb.body.setAngularVelocity(ps.builder.unpackFloat(-1000, 1000, RES_VELOCITY));

                    tempMassData.mass = ps.builder.unpackFloat(0, 1000, RES_MASS);
                    tempMassData.center.set(ps.builder.unpackFloat(-1,  11, RES_POSITION),
                            ps.builder.unpackFloat(-1, 11, RES_POSITION));
                    tempMassData.I = ps.builder.unpackFloat(-1000, 1000, RES_MASS);
                    pb.body.setMassData(tempMassData);
                }

                return 0;
            }

            @Override
            int serialize(PacketReader ps) {
                int i = 0;
                ImmutableArray<Entity> entities = ps.engine.getSystem(GameDirector.class).getEntities();
//                ps.byteBuffer.putInt(entities.size());
                i += ps.builder.packInt(entities.size(),0, 255);
                for (Entity entity : entities) {
                    int id = EntityUtils.getId(entity);
                    i += ps.builder.packInt(id, 0, 255);

                    PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
                    Transform tfm = pb.body.getTransform();
                    i += ps.builder.packFloat(tfm.vals[0], -1, 11, RES_POSITION);
                    i += ps.builder.packFloat(tfm.vals[1], -1, 11, RES_POSITION);
                    i += ps.builder.packFloat(tfm.getRotation(), 0, MAX_ORIENTATION, RES_ORIENTATION);

                    Vector2 vel = pb.body.getLinearVelocity();
                    i += ps.builder.packFloat(vel.x, -1000, 1000, RES_VELOCITY);
                    i += ps.builder.packFloat(vel.y, -1000, 1000, RES_VELOCITY);
                    i += ps.builder.packFloat(pb.body.getAngularVelocity(), -1000, 1000,  RES_VELOCITY);

                    MassData md = pb.body.getMassData();
                    i +=  ps.builder.packFloat(md.mass, 0, 1000, RES_MASS);
                    i +=  ps.builder.packFloat(md.center.x,  -1,  11, RES_POSITION);
                    i +=  ps.builder.packFloat(md.center.y,  -1,  11, RES_POSITION);
                    i +=  ps.builder.packFloat(md.I, 0, 1000, RES_MASS);
                }

                return i;
            }
        },
        SystemSnapshot() {
            @Override
            int deserialize(PacketReader ps) {
                int count = ps.builder.unpackInt(0, 255);
                while (count-- > 0) {
//                    deserializeEntity(ps);
                }
                return 0;
            }

            @Override
            int serialize(PacketReader ps) {
                int i = 0;
                ImmutableArray<Entity> entities = ps.engine.getSystem(GameDirector.class).getEntities();
//                ps.byteBuffer.putInt(entities.size());
                i += ps.builder.packInt(entities.size(),0, 255);
                for (Entity entity : entities) {
//                    i += serializeEntity(ps, entity, true);
                }

                return 0;
            }
        };

        private static MassData tempMassData = new MassData();

//        private static int deserializeEntity(PacketReader ps) {
//            Packet.PacketBuilder pb = ps.builder;
//            Entity entity = ps.engine.createEntity();
//
//            IdentityComponent identity = ComponentFactory.identity(ps.engine, pb.unpackInt(0, 255));
//            PhysicsBodyComponent physicsBody = ComponentFactory.physicsBody(ps.engine,
//                    BodyDef.BodyType.values()[pb.unpackInt(0, BodyDef.BodyType.values().length)], pb.unpackFloat(-1,  11,  RES_POSITION), pb.unpackFloat(-1, 11,  RES_POSITION),
//                    0, pb.unpackFloat(0, 10,  RES_POSITION),
//                    pb.unpackFloat(0, 10, RES_MASS), pb.unpackFloat(0, 10, RES_MASS), pb.unpackFloat(0, 10, RES_MASS));
//            Texture tx = ComponentFactory.textureCircle(32, 16, Color.WHITE);
//            GraphicsComponent graphics = ComponentFactory.graphics(ps.engine, new TextureRegion(tx), pb.unpackFloat(0, 100, RES_POSITION), pb.unpackFloat(0, 100, RES_POSITION));
//
//            entity.add(identity);
//            entity.add(physicsBody);
//            entity.add(graphics);
//
//            ps.engine.addEntity(entity);
//
//            return 0;
//        }
//
//        private static int serializeEntity(PacketReader ps, Entity entity, boolean full) {
//            int i = 0;
//
//            int id = EntityUtils.getId(entity);
//            i += ps.builder.packInt(id, 0, 255);
//
//            PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
//            i += ps.builder.packInt(pb.bodyDef.type.getValue(), 0, BodyDef.BodyType.values().length);
//            i += ps.builder.packFloat(pb.bodyDef.position.x, -1, 11, RES_POSITION);
//            i += ps.builder.packFloat(pb.bodyDef.position.y, -1, 11, RES_POSITION);
//            // add shape type
//            i += ps.builder.packFloat(pb.fixtureDef.shape.getRadius(), 0, 10, RES_POSITION);
//            i += ps.builder.packFloat(pb.fixtureDef.density, 0, 10, RES_MASS);
//            i += ps.builder.packFloat(pb.fixtureDef.friction, 0, 10, RES_MASS);
//            i += ps.builder.packFloat(pb.fixtureDef.restitution, 0, 10, RES_MASS);
//
//            GraphicsComponent g = ComponentMappers.graphics.get(entity);
//            // add texture type
//            i += ps.builder.packFloat(g.spriteActor.getSprite().getWidth(), 0, 100, RES_POSITION);
//            i += ps.builder.packFloat(g.spriteActor.getSprite().getHeight(), 0, 100, RES_POSITION);
//
//            return i;
//        }

        abstract int deserialize(PacketReader ps);
        abstract int serialize(PacketReader ps);
    }
}
