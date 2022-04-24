package com.xam.bobgame.net;

import com.badlogic.gdx.math.MathUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.nio.ByteBuffer;

public class PacketBuilderTest {

    @Test
    public void testInt() {
        int count = 29;
        int[] testInts = new int[count];

        ByteBuffer buffer = ByteBuffer.allocate(count * 4);
        PacketBuilder packetBuilder = new PacketBuilder(buffer);
        int limit = 1;
        for (int i = 0; i < count; ++i) {
            limit *= 2;
            testInts[i] = MathUtils.random(0, limit * 2 - 1) - limit;
            packetBuilder.packInt(testInts[i], -limit, limit-1);
        }

        buffer.rewind();

        limit = 1;
        for (int i = 0; i < count; ++i) {
            limit *= 2;
            int a = packetBuilder.getInt(-limit, limit-1);
            System.out.println("" + testInts[i] + " ? " + a);
            Assertions.assertEquals(testInts[i], a);
        }
    }

    @Test
    public void testFloat() {
        int count = 29;
        float[] testFloats = new float[count];

        ByteBuffer buffer = ByteBuffer.allocate(count * 4);
        PacketBuilder packetBuilder = new PacketBuilder(buffer);
        int limit = 1;
        float res = 0.001f;
        for (int i = 0; i < count; ++i) {
            limit *= 2;
            testFloats[i] = res * MathUtils.random(0, limit * 2) - res * limit;
            packetBuilder.packFloat(testFloats[i], -res * limit, res * limit, res);
        }

        buffer.rewind();

        limit = 1;
        for (int i = 0; i < count; ++i) {
            limit *= 2;
            float a = packetBuilder.getFloat(-res * limit, res * limit, res);
            System.out.println("" + i + " " + testFloats[i] + " ? " + a);
            Assertions.assertEquals(testFloats[i], a);
        }
    }

    public void testFloats(int count, float min, float max, float res) {
        float[] testFloats = new float[count];

        ByteBuffer buffer = ByteBuffer.allocate(count * 4);
        PacketBuilder packetBuilder = new PacketBuilder(buffer);
        for (int i = 0; i < count; ++i) {
            testFloats[i] = MathUtils.random(min, max);
            packetBuilder.packFloat(testFloats[i], min, max, res);
        }

        buffer.rewind();

        for (int i = 0; i < count; ++i) {
            float a = packetBuilder.getFloat(min, max, res);
            System.out.println("" + i + " " + testFloats[i] + " ? " + a + " diff: " + (testFloats[i] - a));
            Assertions.assertTrue(testFloats[i] - a < res);
        }
    }

    @Test
    public void testSpecifiedFloats() {
        testFloats(30, -100f, 100f, 0.0001f);
    }

//    @Test
//    public void numbers() {
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
//    }
}