package com.xam.bobgame.net;

import com.badlogic.gdx.math.MathUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

public class PacketBuilderTest {

    @Test
    public void testInt() {
        int count = 29;
        int[] testInts = new int[count];
        int[] bitCounts = new int[count];

        Packet packet = new Packet(count * 4);
        Packet.PacketBuilder packetBuilder = new Packet.PacketBuilder(packet);
        int limit = 1;
        for (int i = 0; i < count; ++i) {
            limit *= 2;
            testInts[i] = MathUtils.random(0, limit * 2 - 1) - limit;
            bitCounts[i] = packetBuilder.packInt(testInts[i], -limit, limit-1);
        }

        packetBuilder.flush(true);
        packetBuilder.rewind();

        limit = 1;
        for (int i = 0; i < count; ++i) {
            limit *= 2;
            int a = packetBuilder.unpackInt(-limit, limit-1);
            System.out.println("" + bitCounts[i] + " " + testInts[i] + " ? " + a);
            Assertions.assertEquals(testInts[i], a);
        }
    }

    @Test
    public void testInt1() {
        int i = 1;
        Packet packet = new Packet(32);
        Packet.PacketBuilder packetBuilder = new Packet.PacketBuilder(packet);
        System.out.println("" + packetBuilder.packInt(1, 0, 1));
        System.out.println("" + packetBuilder.packInt(1, 0, 2));

        packetBuilder.flush(true);
        packetBuilder.rewind();

//        System.out.println("" + buffer.get());
        System.out.println("" + packetBuilder.unpackInt(0, 1));
        System.out.println("" + packetBuilder.unpackInt(0, 2));
    }

    @Test
    public void testFloat() {
        int count = 29;
        float[] testFloats = new float[count];

        Packet packet = new Packet(count * 4);
        Packet.PacketBuilder packetBuilder = new Packet.PacketBuilder(packet);
        int limit = 1;
        float res = 0.001f;
        for (int i = 0; i < count; ++i) {
            limit *= 2;
            testFloats[i] = res * MathUtils.random(0, limit * 2) - res * limit;
            packetBuilder.packFloat(testFloats[i], -res * limit, res * limit, res);
        }

        packetBuilder.flush(true);
        packetBuilder.rewind();

        limit = 1;
        for (int i = 0; i < count; ++i) {
            limit *= 2;
            float a = packetBuilder.unpackFloat(-res * limit, res * limit, res);
            System.out.println("" + i + " " + testFloats[i] + " ? " + a);
            Assertions.assertEquals(testFloats[i], a);
        }
    }

    public void testFloats(int count, float min, float max, float res) {
        float[] testFloats = new float[count];

        Packet packet = new Packet(count * 4);
        Packet.PacketBuilder packetBuilder = new Packet.PacketBuilder(packet);
        for (int i = 0; i < count; ++i) {
            testFloats[i] = MathUtils.random(min, max);
            packetBuilder.packFloat(testFloats[i], min, max, res);
        }

        packetBuilder.flush(true);
        packetBuilder.rewind();

        for (int i = 0; i < count; ++i) {
            float a = packetBuilder.unpackFloat(min, max, res);
            System.out.println("" + i + " " + testFloats[i] + " ? " + a + " diff: " + (testFloats[i] - a));
            Assertions.assertTrue(testFloats[i] - a < res);
        }
    }

    @Test
    public void testSpecifiedFloats() {
        testFloats(30, -500f, 500f, 1e-6f);
    }

    @Test
    public void numbers() {
//        byte[] bytes = new byte[256];
//        byte j = -128;
//        for (int i = 0; i < 256; ++i) {
//            bytes[i] = j;
//            j++;
//        }
//
//        byte a = -1, b = 1;
//        int z = ((a & 255) << 8) | (b & 255);
//        System.out.println("" + a + " " + b + " " + z);
//        System.out.println("" + (((byte) ((533 >> 8) & 255) & 255) << 8));
//
//        for (int i = 0; i < 256; ++i) {
//            System.out.println("" + bytes[i] + " " + ((bytes[i] & 255) << 8) + " " + (((int) bytes[i]) << 8));
//        }

        System.out.println("" + (-1 & 4294967295L) );
    }
}