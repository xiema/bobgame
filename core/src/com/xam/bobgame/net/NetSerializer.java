package com.xam.bobgame.net;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.Serializer;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;

import java.nio.ByteBuffer;

public class NetSerializer extends Serializer<Packet> {
    private Packet readBuffer;

    public NetSerializer(int size) {
        readBuffer = new Packet(size);
    }

    @Override
    public void write(Kryo kryo, Output output, Packet packet) {
        ByteBuffer object = packet.byteBuffer;
        if (object.hasArray()) {
            output.writeBytes(object.array(), object.position(), object.remaining());
            object.position(object.limit());
        }
        else {
            while (object.hasRemaining()) {
                output.writeByte(object.get());
            }
        }
    }

    @Override
    public Packet read(Kryo kryo, Input input, Class<? extends Packet> type) {
        int l = input.limit() - input.position();
        readBuffer.byteBuffer.clear();
        input.readBytes(readBuffer.byteBuffer.array(), 0, l);
        readBuffer.byteBuffer.limit(l);
        return readBuffer;
    }
}
