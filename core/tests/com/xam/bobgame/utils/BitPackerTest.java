package com.xam.bobgame.utils;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.net.Message;
import com.xam.bobgame.utils.BitPacker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;

import java.nio.ByteBuffer;

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
    public void testRandomLimit() {
        int count = 29;
        int[] testInts = new int[count];
        int[] testLimitsLower = new int[count];
        int[] testLimitsUpper = new int[count];
        int[] bitCounts = new int[count];

        Message message = new Message(count * 4);
        BitPacker bitPacker = new BitPacker(message.getByteBuffer());
        for (int i = 0; i < count; ++i) {
            long low = MathUtils.random(-(1L << 31), (1L << 31) - 1);
            long up = MathUtils.random(low, (1L << 31) - 1);
            long num = MathUtils.random(low, up);
            testLimitsLower[i] = (int) (low);
            testLimitsUpper[i] = (int) (up);
            testInts[i] = (int) (num);
            bitCounts[i] = bitPacker.packInt(testInts[i], testLimitsLower[i], testLimitsUpper[i]);
        }

        bitPacker.flush(true);
        bitPacker.rewind();

        for (int i = 0; i < count; ++i) {
            int a = bitPacker.unpackInt(testLimitsLower[i], testLimitsUpper[i]);
            System.out.println("" + i + " low=" + testLimitsLower[i] + " up=" + testLimitsUpper[i] + " count=" + bitCounts[i] + ": " + testInts[i] + " ? " + a);
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
    public void testNatural() {
        int count = 50;
        ByteBuffer byteBuffer1 = ByteBuffer.allocate(count * 8);
        ByteBuffer byteBuffer2 = ByteBuffer.allocate(count * 8);
        BitPacker bitPacker = new BitPacker(byteBuffer1);

        int[] nums = new int[count];

//        CRC32 crc32 = new CRC32();

        for (int i = 0; i < count; ++i) {
            int x = (int) MathUtils.random((long) Integer.MIN_VALUE, (long) Integer.MAX_VALUE);

            nums[i] = x;
//            byteBuffer1.putInt(x);
//            byteBuffer2.putInt(x);
            bitPacker.packInt(x);
//            crc32.update(x);

//            Log.info("b1=" + byteBuffer1.getLong(byteBuffer1.position() - 8) + " b2=" + byteBuffer2.getInt(byteBuffer2.position() - 8));
        }

//        byteBuffer1.flip();
//        byteBuffer2.flip();
//        bitPacker.setBuffer(byteBuffer2);
        bitPacker.flush(true);

//        Assertions.assertEquals(crc32.getValue(), bitPacker.getCRC(), "Check CRC");

        for (int i = 0; i < count; ++i) {
//            int b1 = byteBuffer1.getInt();
            int b2 = bitPacker.unpackInt();

            Assertions.assertEquals(nums[i], b2, "Failed index " + i);
//            Log.info("b1=" + byteBuffer1.getLong(byteBuffer1.position() - 8) + " b2=" + byteBuffer2.getInt(byteBuffer2.position() - 8));
        }
    }

    @Test
    public void testSkip() {
        ByteBuffer buffer = ByteBuffer.allocate(64);
        BitPacker bitPacker = new BitPacker(buffer);

        bitPacker.packInt(23);
        bitPacker.packInt(32);
        bitPacker.padToLong();
        bitPacker.packInt(1, 0, 1);
        bitPacker.padToLong();
        bitPacker.packInt(4, 0, 7);
        bitPacker.padToLong();
        bitPacker.packInt(23, 0, 31);
        bitPacker.flush(true);

        bitPacker.setBuffer(buffer);
        Assertions.assertEquals(23, bitPacker.unpackInt());
        Assertions.assertEquals(32, bitPacker.unpackInt());
        bitPacker.skipToLong();
        Assertions.assertEquals(1, bitPacker.unpackInt(0, 1));
        bitPacker.skipToLong();
        Assertions.assertEquals(4, bitPacker.unpackInt(0, 7));
        bitPacker.skipToLong();
        Assertions.assertEquals(23, bitPacker.unpackInt(0, 31));
    }

    @Test
    public void testSkip2() {
        ByteBuffer buffer = ByteBuffer.allocate(64);
        BitPacker bitPacker = new BitPacker(buffer);

        bitPacker.packInt(23);
        bitPacker.packInt(32);
        bitPacker.padToNextByte();
        bitPacker.padToNextByte();
        bitPacker.packInt(3, 0, 5);
        bitPacker.padToNextByte();
        bitPacker.packInt(4, 0, 7);
        bitPacker.padToNextByte();
        bitPacker.packInt(23, 0, 31);
        bitPacker.flush(true);

        bitPacker.setBuffer(buffer);
        Assertions.assertEquals(23, bitPacker.unpackInt());
        Assertions.assertEquals(32, bitPacker.unpackInt());
        bitPacker.skipToNextByte();
        Assertions.assertEquals(3, bitPacker.unpackInt(0, 5));
        bitPacker.skipToNextByte();
        Assertions.assertEquals(4, bitPacker.unpackInt(0, 7));
        bitPacker.skipToNextByte();
        Assertions.assertEquals(23, bitPacker.unpackInt(0, 31));
    }

    @Test
    public void testIntBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(64);
        BitPacker bitPacker = new BitPacker(buffer);
        bitPacker.packInt(120);
        bitPacker.packInt(120);
        bitPacker.flush(true);
//        buffer.putInt(120);
//        buffer.putInt(120);

        for (int i = 0; i < 8; ++i) {
            Log.info("byte " + i + ": " + buffer.array()[i]);
        }
    }

    @Test
    public void testFloat() {
        int count = 29;
        float[] testFloats = new float[count];

        Message message = new Message(count * 4);
        BitPacker bitPacker = new BitPacker(message.getByteBuffer());
        int limit = 1;
        float res = 1e-8f;
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

    @Test
    public void testFloat2() {
        int count = 1000000;
        float minFloat = 0;
        float maxFloat = 10;
        float[] testFloats = new float[count];
        float[] res = new float[count];
        float[] lowerLimits = new float[count];
        float[] upperLimits = new float[count];

        Message message = new Message(count * 4);
        BitPacker bitPacker = new BitPacker(message.getByteBuffer());
        for (int i = 0; i < count; ++i) {
            res[i] = (float) Math.pow(2, -MathUtils.random(24));
            lowerLimits[i] = MathUtils2.quantize(MathUtils.random(minFloat, maxFloat - res[i]), res[i]);
            upperLimits[i] = MathUtils2.quantize(MathUtils.random(lowerLimits[i] + res[i], maxFloat), res[i]);
            testFloats[i] = MathUtils2.quantize(MathUtils.random(lowerLimits[i], upperLimits[i]), res[i]);
            bitPacker.packFloat(testFloats[i], lowerLimits[i], upperLimits[i], res[i]);
        }

        bitPacker.flush(true);
        bitPacker.rewind();

        float error = 0, weightedError = 0;
        int errCount = 0;
        for (int i = 0; i < count; ++i) {
            float a = bitPacker.unpackFloat(lowerLimits[i], upperLimits[i], res[i]);
            float err = Math.abs(a - testFloats[i]);
            if (err > res[i]) {
                error += err;
                weightedError += err / res[i];
                errCount++;
            }
//            Assertions.assertTrue(, "(" + i + ") Compare " + testFloats[i] + " ? " + a + " (range " + lowerLimits[i] + " to " + upperLimits[i] + ", res " + res[i] + ")");
        }

        Log.info("Error count: " + errCount + " (" + ((float) errCount / count * 100) + "%) Abs error: " + (error / errCount) + " Weighted: " + (weightedError / errCount));
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
        testFloats(30, -500f, 500f, (float) Math.pow(2, -16));
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

        System.out.println("" + ( (int) ((1L << 32 - 1) )) );
    }
}