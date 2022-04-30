package com.xam.bobgame.net;

import com.badlogic.gdx.math.MathUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

public class BitPackerTest {

    @Test
    public void testInt() {
        int count = 29;
        int[] testInts = new int[count];
        int[] bitCounts = new int[count];

        Message message = new Message(count * 4);
        BitPacker bitPacker = new BitPacker(message.getByteBuffer());
        int limit = 1;
        for (int i = 0; i < count; ++i) {
            limit *= 2;
            testInts[i] = MathUtils.random(0, limit * 2 - 1) - limit;
            bitCounts[i] = bitPacker.packInt(testInts[i], -limit, limit-1);
        }

        bitPacker.flush(true);
        bitPacker.rewind();

        limit = 1;
        for (int i = 0; i < count; ++i) {
            limit *= 2;
            int a = bitPacker.unpackInt(-limit, limit-1);
            System.out.println("" + bitCounts[i] + " " + testInts[i] + " ? " + a);
            Assertions.assertEquals(testInts[i], a);
        }
    }

    @Test
    public void testInt1() {
        int i = 1;
        Message message = new Message(32);
        BitPacker bitPacker = new BitPacker(message.getByteBuffer());
//        System.out.println("" + packetBuilder.packInt(1, 0, 1));
//        System.out.println("" + packetBuilder.packInt(1, 0, 2));
        bitPacker.packInt(1, -1, 30);

        bitPacker.flush(true);
        bitPacker.rewind();

//        System.out.println("" + buffer.get());
        System.out.println("" + bitPacker.unpackInt(-1, 30));
//        System.out.println("" + packetBuilder.unpackInt(0, 2));
    }

    @Test
    public void testFloat() {
        int count = 29;
        float[] testFloats = new float[count];

        Message message = new Message(count * 4);
        BitPacker bitPacker = new BitPacker(message.getByteBuffer());
        int limit = 1;
        float res = 0.001f;
        for (int i = 0; i < count; ++i) {
            limit *= 2;
            testFloats[i] = res * MathUtils.random(0, limit * 2) - res * limit;
            bitPacker.packFloat(testFloats[i], -res * limit, res * limit, res);
        }

        bitPacker.flush(true);
        bitPacker.rewind();

        limit = 1;
        for (int i = 0; i < count; ++i) {
            limit *= 2;
            float a = bitPacker.unpackFloat(-res * limit, res * limit, res);
            System.out.println("" + i + " " + testFloats[i] + " ? " + a);
            Assertions.assertEquals(testFloats[i], a);
        }
    }

    public void testFloats(int count, float min, float max, float res) {
        float[] testFloats = new float[count];

        Message message = new Message(count * 4);
        BitPacker bitPacker = new BitPacker(message.getByteBuffer());
        for (int i = 0; i < count; ++i) {
            testFloats[i] = MathUtils.random(min, max);
            bitPacker.packFloat(testFloats[i], min, max, res);
        }

        bitPacker.flush(true);
        bitPacker.rewind();

        for (int i = 0; i < count; ++i) {
            float a = bitPacker.unpackFloat(min, max, res);
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