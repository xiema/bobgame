package com.xam.bobgame.net;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.utils.ImmutableArray;
import com.xam.bobgame.GameDirector;
import com.xam.bobgame.components.PositionComponent;
import com.xam.bobgame.components.VelocityComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityUtils;
import com.xam.bobgame.utils.DebugUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

public class PacketSerializer {
    PacketBuilder packetBuilder = new PacketBuilder();
    ByteBuffer byteBuffer;
    Engine engine;
    CRC32 crc32 = new CRC32();

    public void syncEngine(ByteBuffer byteBuffer, Engine engine) {
        this.byteBuffer = byteBuffer;
        this.engine = engine;
        packetBuilder.setBuffer(byteBuffer);

        int length = byteBuffer.getInt();
        if (State.Start.deserialize(this) == -1) {
//            DebugUtils.error("PacketSerializer", "Sync engine to server failed");
        }
    }

    public int read(InputStream input, ByteBuffer byteBuffer) {
        int dataSize;
        int b = byteBuffer.position();
        byte bytes[] = byteBuffer.array();

        try {
            if (input.available() < Packet.HEADER_SIZE || input.read(bytes, b, Packet.HEADER_SIZE) < Packet.HEADER_SIZE) return -1;
            dataSize = byteBuffer.getInt(b + 4);
            if (dataSize + b + Packet.HEADER_SIZE > byteBuffer.limit()) {
                DebugUtils.error("PacketBuffer", "Packet too long");
                input.skip(input.available());
                return -1;
            }
            input.read(bytes, b + Packet.HEADER_SIZE, dataSize);
        }
        catch (IOException e) {
            e.printStackTrace();
            return -1;
        }

        crc32.reset();
        crc32.update(bytes, b + 4, dataSize + Packet.HEADER_SIZE - 4);
        if ((int) crc32.getValue() != byteBuffer.getInt(b)) {
            DebugUtils.error("PacketBuffer", "Bad CRC");
            try {
                input.skip(input.available());
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            return -1;
        }
        return dataSize + Packet.HEADER_SIZE;
    }

    public boolean serialize(ByteBuffer byteBuffer, Engine engine) {
        this.byteBuffer = byteBuffer;
        this.engine = engine;
        packetBuilder.setBuffer(byteBuffer);
        int i = byteBuffer.position();
        byteBuffer.position(i + Packet.HEADER_SIZE);
        int l = State.Start.serialize(this);
        byteBuffer.putInt(i + 4, l);
        crc32.reset();
        crc32.update(byteBuffer.array(), i + 4, l + Packet.HEADER_SIZE - 4);
        byteBuffer.putInt(i, (int) crc32.getValue());
        return true;
    }

    private enum State {
        Start() {
            @Override
            int deserialize(PacketSerializer ps) {
                return Command.deserialize(ps);
            }
            @Override
            int serialize(PacketSerializer ps) {
                return Command.serialize(ps);
            }
        },
        Command() {
            @Override
            int deserialize(PacketSerializer ps) {
                while (ps.byteBuffer.hasRemaining()) {
                    byte b = ps.byteBuffer.get();
                    int i = -1;
                    switch (b) {
                        case 1:
                            i = SystemUpdate.deserialize(ps);
                            break;
                    }
                    if (i == -1) return -1;
                }

                return 0;
            }
            @Override
            int serialize(PacketSerializer ps) {
                int i = 1, j;
                ps.byteBuffer.put((byte) 1);
                if ((j = SystemUpdate.serialize(ps)) == -1) return -1;
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
                int cnt = ps.packetBuilder.getInt(0, 255);
                while (cnt-- > 0) {
//                    int id = ps.byteBuffer.getInt();
                    int id = ps.packetBuilder.getInt(0, 255);
                    while (EntityUtils.getId(entities.get(i)) != id) {
                        i = (i + 1) % entities.size();
                    }
                    Entity entity = entities.get(i);
                    ComponentMappers.position.get(entity).vec.set(ps.packetBuilder.getFloat(-500, 500, 0.001f), ps.packetBuilder.getFloat(-500, 500, 0.001f));
                    ComponentMappers.velocity.get(entity).vec.set(ps.packetBuilder.getFloat(-500, 500, 0.001f), ps.packetBuilder.getFloat(-500, 500, 0.001f));
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
                    PositionComponent position = ComponentMappers.position.get(entity);
                    VelocityComponent velocity = ComponentMappers.velocity.get(entity);
//                    ps.byteBuffer.putInt(id);
//                    ps.byteBuffer.putFloat(position.vec.x);
//                    ps.byteBuffer.putFloat(position.vec.y);
//                    ps.byteBuffer.putFloat(velocity.vec.x);
//                    ps.byteBuffer.putFloat(velocity.vec.y);
                    i += ps.packetBuilder.packInt(id, 0, 255);
                    i += ps.packetBuilder.packFloat(position.vec.x, -500, 500, 0.001f);
                    i += ps.packetBuilder.packFloat(position.vec.y, -500, 500, 0.001f);
                    i += ps.packetBuilder.packFloat(velocity.vec.x, -500, 500, 0.001f);
                    i += ps.packetBuilder.packFloat(velocity.vec.y, -500, 500, 0.001f);
                }

                return i;
            }
        };

        abstract int deserialize(PacketSerializer ps);
        abstract int serialize(PacketSerializer ps);
    }
}
