package com.xam.bobgame.net;

import java.nio.ByteBuffer;

public class Packet {
    public ByteBuffer byteBuffer;

    public Packet(int size) {
        byteBuffer = ByteBuffer.allocate(size);
    }
}
