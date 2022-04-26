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
import com.xam.bobgame.utils.DebugUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

public class PacketSerializer {
    public static final float RES_POSITION = 1e-8f;
    public static final float RES_ORIENTATION = 1e-8f;
    public static final float RES_VELOCITY = 1e-8f;
    public static final float RES_MASS = 1e-8f;
    public static final float MAX_ORIENTATION = 3.14159f;

    PacketBuilder packetBuilder = new PacketBuilder();
    Engine engine;
    CRC32 crc32 = new CRC32();
    int option = 1;

    public void syncEngine(ByteBuffer byteBuffer, Engine engine) {
        this.engine = engine;
        packetBuilder.setBuffer(byteBuffer);

        int length = byteBuffer.getInt();
        if (State.Start.deserialize(this) == -1) {
//            DebugUtils.error("PacketSerializer", "Sync engine to server failed");
        }
        packetBuilder.clear();
    }

    public boolean checkCRC(int crcHash, byte[] bytes, int i, int l) {
        crc32.reset();
        crc32.update(bytes, i, l);
        return ((int) crc32.getValue()) == crcHash;
    }

    public boolean checkCRC(int crcHash, ByteBuffer byteBuffer) {
        crc32.reset();
        while (byteBuffer.hasRemaining()) {
            crc32.update(byteBuffer.get());
        }
        return ((int) crc32.getValue()) == crcHash;
    }

    public int read(InputStream input, ByteBuffer byteBuffer) {
        int dataSize;
        int b = byteBuffer.position();
        byte bytes[] = byteBuffer.array();

        try {
            if (input.available() < Net.HEADER_SIZE || input.read(bytes, b, Net.HEADER_SIZE) < Net.HEADER_SIZE) return -1;
            dataSize = byteBuffer.getInt(b + 4);
            if (dataSize + b + Net.HEADER_SIZE > byteBuffer.limit()) {
                DebugUtils.error("PacketBuffer", "Packet too long");
                input.skip(input.available());
                return -1;
            }
            input.read(bytes, b + Net.HEADER_SIZE, dataSize);
        }
        catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        crc32.reset();
        crc32.update(bytes, b + 4, dataSize + Net.HEADER_SIZE - 4);
        if ((int) crc32.getValue() != byteBuffer.getInt(b)) {
            DebugUtils.error("PacketBuffer", "Bad CRC:");
            DebugUtils.log("Packet", DebugUtils.bytesHex(bytes));
            zeroBytes(bytes);
            try {
                input.skip(input.available());
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            return -1;
        }
        return dataSize + Net.HEADER_SIZE;
    }

    private void zeroBytes(byte[] bytes) {
        int i = bytes.length;
        while (i-- > 0) {
            bytes[i] = 0;
        }
    }

    public int serialize(ByteBuffer byteBuffer, Engine engine) {
        this.engine = engine;
        packetBuilder.setBuffer(byteBuffer);
        int i = byteBuffer.position();
        byteBuffer.position(i + Net.HEADER_SIZE);
        int bits = State.Start.serialize(this);
        int l = (bits + 7) / 8;
        byteBuffer.putInt(i + 4, l);
        crc32.reset();
        crc32.update(byteBuffer.array(), i + 4, l + Net.HEADER_SIZE - 4);
        byteBuffer.putInt(i, (int) crc32.getValue());
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
                return Command.serialize(ps) + ps.packetBuilder.flush();
            }
        },
        Command() {
            @Override
            int deserialize(PacketSerializer ps) {
                while (ps.packetBuilder.hasRemaining()) {
                    byte b = ps.packetBuilder.unpackByte();
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
                i += ps.packetBuilder.packByte((byte) ps.option);
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
                int cnt = ps.packetBuilder.unpackInt(0, 255);
                while (cnt-- > 0) {
//                    int id = ps.byteBuffer.getInt();
                    int id = ps.packetBuilder.unpackInt(0, 255);
                    while (EntityUtils.getId(entities.get(i)) != id) {
                        i = (i + 1) % entities.size();
                    }
                    Entity entity = entities.get(i);
                    PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
                    pb.body.setTransform(ps.packetBuilder.unpackFloat(-1,11, RES_POSITION),
                            ps.packetBuilder.unpackFloat(-1,11, RES_POSITION),
                            ps.packetBuilder.unpackFloat(0,MAX_ORIENTATION, RES_ORIENTATION));

                    pb.body.setLinearVelocity(ps.packetBuilder.unpackFloat(-1000, 1000, RES_VELOCITY),
                            ps.packetBuilder.unpackFloat(-1000, 1000, RES_VELOCITY));
                    pb.body.setAngularVelocity(ps.packetBuilder.unpackFloat(-1000, 1000, RES_VELOCITY));

                    tempMassData.mass = ps.packetBuilder.unpackFloat(0, 1000, RES_MASS);
                    tempMassData.center.set(ps.packetBuilder.unpackFloat(-1,  11, RES_POSITION),
                            ps.packetBuilder.unpackFloat(-1, 11, RES_POSITION));
                    tempMassData.I = ps.packetBuilder.unpackFloat(-1000, 1000, RES_MASS);
                    pb.body.setMassData(tempMassData);
                }

                return 0;
            }

            @Override
            int serialize(PacketSerializer ps) {
                int i = 0;
                ImmutableArray<Entity> entities = ps.engine.getSystem(GameDirector.class).getEntities();
//                ps.byteBuffer.putInt(entities.size());
                i += ps.packetBuilder.packInt(entities.size(),0, 255);
                for (Entity entity : entities) {
                    int id = EntityUtils.getId(entity);
                    i += ps.packetBuilder.packInt(id, 0, 255);

                    PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
                    Transform tfm = pb.body.getTransform();
                    i += ps.packetBuilder.packFloat(tfm.vals[0], -1, 11, RES_POSITION);
                    i += ps.packetBuilder.packFloat(tfm.vals[1], -1, 11, RES_POSITION);
                    i += ps.packetBuilder.packFloat(tfm.getRotation(), 0, MAX_ORIENTATION, RES_ORIENTATION);

                    Vector2 vel = pb.body.getLinearVelocity();
                    i += ps.packetBuilder.packFloat(vel.x, -1000, 1000, RES_VELOCITY);
                    i += ps.packetBuilder.packFloat(vel.y, -1000, 1000, RES_VELOCITY);
                    i += ps.packetBuilder.packFloat(pb.body.getAngularVelocity(), -1000, 1000,  RES_VELOCITY);

                    MassData md = pb.body.getMassData();
                    i +=  ps.packetBuilder.packFloat(md.mass, 0, 1000, RES_MASS);
                    i +=  ps.packetBuilder.packFloat(md.center.x,  -1,  11, RES_POSITION);
                    i +=  ps.packetBuilder.packFloat(md.center.y,  -1,  11, RES_POSITION);
                    i +=  ps.packetBuilder.packFloat(md.I, 0, 1000, RES_MASS);
                }

                return i;
            }
        },
        SystemSnapshot() {
            @Override
            int deserialize(PacketSerializer ps) {
                int count = ps.packetBuilder.unpackInt(0, 255);
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
                i += ps.packetBuilder.packInt(entities.size(),0, 255);
                for (Entity entity : entities) {
                    i += serializeEntity(ps, entity, true);
                }

                return 0;
            }
        };

        private static MassData tempMassData = new MassData();

        private static int deserializeEntity(PacketSerializer ps) {
            PacketBuilder pb = ps.packetBuilder;
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
            i += ps.packetBuilder.packInt(id, 0, 255);

            PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
            i += ps.packetBuilder.packInt(pb.bodyDef.type.getValue(), 0, BodyDef.BodyType.values().length);
            i += ps.packetBuilder.packFloat(pb.bodyDef.position.x, -1, 11, RES_POSITION);
            i += ps.packetBuilder.packFloat(pb.bodyDef.position.y, -1, 11, RES_POSITION);
            // add shape type
            i += ps.packetBuilder.packFloat(pb.fixtureDef.shape.getRadius(), 0, 10, RES_POSITION);
            i += ps.packetBuilder.packFloat(pb.fixtureDef.density, 0, 10, RES_MASS);
            i += ps.packetBuilder.packFloat(pb.fixtureDef.friction, 0, 10, RES_MASS);
            i += ps.packetBuilder.packFloat(pb.fixtureDef.restitution, 0, 10, RES_MASS);

            GraphicsComponent g = ComponentMappers.graphics.get(entity);
            // add texture type
            i += ps.packetBuilder.packFloat(g.spriteActor.getSprite().getWidth(), 0, 100, RES_POSITION);
            i += ps.packetBuilder.packFloat(g.spriteActor.getSprite().getHeight(), 0, 100, RES_POSITION);

            return i;
        }

        abstract int deserialize(PacketSerializer ps);
        abstract int serialize(PacketSerializer ps);
    }
}
