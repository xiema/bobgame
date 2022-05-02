package com.xam.bobgame.utils;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.entity.EntityUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class SharedMemoryChecker {

    final int BUFFER_LENGTH = 32;
    final int ENTRY_SIZE = 6 * 4;
    final int BUFFER_SIZE = BUFFER_LENGTH * ENTRY_SIZE;
    RandomAccessFile file;
    MappedByteBuffer sharedBuffer;

    ByteBuffer checkBuffer;

    public SharedMemoryChecker(String name) {
        try {
            file = new RandomAccessFile(name, "rw");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        try {
            sharedBuffer = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, BUFFER_SIZE);
        } catch (IOException e) {
            e.printStackTrace();
        }
        checkBuffer = ByteBuffer.allocateDirect(BUFFER_SIZE);
    }

    private void push(ByteBuffer buffer, Entity entity, int frameNum) {
        int prev = buffer.position();
        int put = (frameNum % BUFFER_LENGTH) * ENTRY_SIZE;

        PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
        Vector2 pos = pb.body.getPosition();
        Vector2 vel = pb.body.getLinearVelocity();

        buffer.position(put);
        buffer.putInt(frameNum);
        buffer.putInt(EntityUtils.getId(entity));
        buffer.putFloat(pos.x);
        buffer.putFloat(pos.y);
        buffer.putFloat(vel.x);
        buffer.putFloat(vel.y);

        if (prev != put) buffer.position(prev);

        if (buffer.remaining() < ENTRY_SIZE) {
            buffer.position(0);
        }
    }

    public void pushShared(Entity entity, int frameNum) {
        push(sharedBuffer, entity, frameNum);
    }

    public void pushCheck(Entity entity, int frameNum) {
        push(checkBuffer, entity, frameNum);
    }

    public void check(Entity entity, int frameNum) {
        sharedBuffer.position((frameNum % BUFFER_LENGTH) * ENTRY_SIZE);
        int i;
        float f;
        int entityId = EntityUtils.getId(entity);
        PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
        Vector2 pos = pb.body.getPosition();
        Vector2 vel = pb.body.getLinearVelocity();

        if (frameNum != (i = sharedBuffer.getInt())) Log.error("SharedMemoryChecker", "Frame numbers don't match: " + frameNum + ", " + i);
        if (entityId != (i = sharedBuffer.getInt())) Log.error("SharedMemoryChecker", "Entity ids don't match: " + entity + ", " + i);
        if (pos.x != (f = sharedBuffer.getFloat())) Log.error("SharedMemoryChecker", "PosX don't match: " + pos.x + ", " + f);
        if (pos.y != (f = sharedBuffer.getFloat())) Log.error("SharedMemoryChecker", "PosY don't match: " + pos.y + ", " + f);
        if (vel.x != (f = sharedBuffer.getFloat())) Log.error("SharedMemoryChecker", "VelX don't match: " + vel.x + ", " + f);
        if (vel.y != (f = sharedBuffer.getFloat())) Log.error("SharedMemoryChecker", "VelY don't match: " + vel.y + ", " + f);
    }
}
