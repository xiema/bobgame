package com.xam.bobgame.net;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.BodyDef;
import com.badlogic.gdx.physics.box2d.MassData;
import com.badlogic.gdx.physics.box2d.Transform;
import com.xam.bobgame.GameDirector;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.components.IdentityComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentFactory;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityUtils;

public class PacketSerializer {
    public static final float RES_POSITION = 1e-8f;
    public static final float RES_ORIENTATION = 1e-8f;
    public static final float RES_VELOCITY = 1e-8f;
    public static final float RES_MASS = 1e-8f;
    public static final float MAX_ORIENTATION = 3.14159f;

    Packet.PacketBuilder builder = new Packet.PacketBuilder();
    Engine engine;
    int option = 1;

    public void syncEngine(Packet packet, Engine engine) {
        this.engine = engine;
        builder.setPacket(packet);

        if (State.Start.deserialize(this) == -1) {
//            DebugUtils.error("PacketSerializer", "Sync engine to server failed");
        }
        builder.clear();
    }

    private void zeroBytes(byte[] bytes) {
        int i = bytes.length;
        while (i-- > 0) {
            bytes[i] = 0;
        }
    }

    public int serialize(Packet packet, Engine engine) {
        this.engine = engine;
        builder.setPacket(packet);
        int bits = State.Start.serialize(this);
        int l = (bits + 7) / 8;
        return l;
    }

    private enum State {
        Start() {
            @Override
            int deserialize(PacketSerializer ps) {
                return Command.deserialize(ps);
            }
            @Override
            int serialize(PacketSerializer ps) {
                return Command.serialize(ps) + ps.builder.flush(true);
            }
        },
        Command() {
            @Override
            int deserialize(PacketSerializer ps) {
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
            int serialize(PacketSerializer ps) {
                int i = 0, j = -1;
                i += ps.builder.packByte((byte) ps.option);
                switch(ps.option) {
                    case 1:
                        j = SystemUpdate.serialize(ps);
                        break;
                    case 2:
                        j = SystemSnapshot.serialize(ps);
                        break;
                }
                if (j == -1) return -1;
                i += j;
                return i;
            }
        },
        SystemUpdate() {
            @Override
            int deserialize(PacketSerializer ps) {
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
            int serialize(PacketSerializer ps) {
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
            int deserialize(PacketSerializer ps) {
                int count = ps.builder.unpackInt(0, 255);
                while (count-- > 0) {
                    deserializeEntity(ps);
                }
                return 0;
            }

            @Override
            int serialize(PacketSerializer ps) {
                int i = 0;
                ImmutableArray<Entity> entities = ps.engine.getSystem(GameDirector.class).getEntities();
//                ps.byteBuffer.putInt(entities.size());
                i += ps.builder.packInt(entities.size(),0, 255);
                for (Entity entity : entities) {
                    i += serializeEntity(ps, entity, true);
                }

                return 0;
            }
        };

        private static MassData tempMassData = new MassData();

        private static int deserializeEntity(PacketSerializer ps) {
            Packet.PacketBuilder pb = ps.builder;
            Entity entity = ps.engine.createEntity();

            IdentityComponent identity = ComponentFactory.identity(ps.engine, pb.unpackInt(0, 255));
            PhysicsBodyComponent physicsBody = ComponentFactory.physicsBody(ps.engine,
                    BodyDef.BodyType.values()[pb.unpackInt(0, BodyDef.BodyType.values().length)], pb.unpackFloat(-1,  11,  RES_POSITION), pb.unpackFloat(-1, 11,  RES_POSITION),
                    0, pb.unpackFloat(0, 10,  RES_POSITION),
                    pb.unpackFloat(0, 10, RES_MASS), pb.unpackFloat(0, 10, RES_MASS), pb.unpackFloat(0, 10, RES_MASS));
            Texture tx = ComponentFactory.textureCircle(32, 16, Color.WHITE);
            GraphicsComponent graphics = ComponentFactory.graphics(ps.engine, new TextureRegion(tx), pb.unpackFloat(0, 100, RES_POSITION), pb.unpackFloat(0, 100, RES_POSITION));

            entity.add(identity);
            entity.add(physicsBody);
            entity.add(graphics);

            ps.engine.addEntity(entity);

            return 0;
        }

        private static int serializeEntity(PacketSerializer ps, Entity entity, boolean full) {
            int i = 0;

            int id = EntityUtils.getId(entity);
            i += ps.builder.packInt(id, 0, 255);

            PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
            i += ps.builder.packInt(pb.bodyDef.type.getValue(), 0, BodyDef.BodyType.values().length);
            i += ps.builder.packFloat(pb.bodyDef.position.x, -1, 11, RES_POSITION);
            i += ps.builder.packFloat(pb.bodyDef.position.y, -1, 11, RES_POSITION);
            // add shape type
            i += ps.builder.packFloat(pb.fixtureDef.shape.getRadius(), 0, 10, RES_POSITION);
            i += ps.builder.packFloat(pb.fixtureDef.density, 0, 10, RES_MASS);
            i += ps.builder.packFloat(pb.fixtureDef.friction, 0, 10, RES_MASS);
            i += ps.builder.packFloat(pb.fixtureDef.restitution, 0, 10, RES_MASS);

            GraphicsComponent g = ComponentMappers.graphics.get(entity);
            // add texture type
            i += ps.builder.packFloat(g.spriteActor.getSprite().getWidth(), 0, 100, RES_POSITION);
            i += ps.builder.packFloat(g.spriteActor.getSprite().getHeight(), 0, 100, RES_POSITION);

            return i;
        }

        abstract int deserialize(PacketSerializer ps);
        abstract int serialize(PacketSerializer ps);
    }
}
