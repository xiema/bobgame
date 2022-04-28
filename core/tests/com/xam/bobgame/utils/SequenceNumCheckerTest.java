package com.xam.bobgame.utils;

import com.esotericsoftware.minlog.Log;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SequenceNumCheckerTest {

    @Test
    public void testSingle() {
        SequenceNumChecker checker = new SequenceNumChecker(192);

        checker.set(32);
        Assertions.assertTrue(checker.get(32));
        Assertions.assertEquals(checker.bits[0], 1L << 32);
        checker.set(100);
        Assertions.assertTrue(checker.get(100));
        Assertions.assertTrue(checker.get(32));
        Assertions.assertEquals(checker.bits[1], 1L << (100 % 64));
        checker.set(191);
        Assertions.assertTrue(checker.get(191));
        Assertions.assertTrue(checker.get(100));
        Assertions.assertTrue(checker.get(32));
        Assertions.assertEquals(checker.bits[2], 1L << (191 % 64));

        checker.set(16);
        Assertions.assertTrue(checker.get(16));
        Assertions.assertTrue(checker.get(191));
        Assertions.assertTrue(checker.get(100));
        Assertions.assertTrue(checker.get(32));

        checker.set(48);
        Assertions.assertTrue(checker.get(48));
        Assertions.assertTrue(checker.get(16));
        Assertions.assertTrue(checker.get(191));
        Assertions.assertTrue(checker.get(100));
        Assertions.assertFalse(checker.get(32));

        checker.set(100);
        Assertions.assertTrue(checker.get(100));
        Assertions.assertTrue(checker.get(48));
        Assertions.assertTrue(checker.get(16));
        Assertions.assertTrue(checker.get(191));
        Assertions.assertFalse(checker.get(32));

        checker.set(190);
        Assertions.assertTrue(checker.get(190));
        Assertions.assertTrue(checker.get(100));
        Assertions.assertTrue(checker.get(48));
        Assertions.assertTrue(checker.get(16));
        Assertions.assertTrue(checker.get(191));
        Assertions.assertFalse(checker.get(32));

        checker.set(12);
        Assertions.assertTrue(checker.get(12));
        Assertions.assertFalse(checker.get(191));
        Assertions.assertTrue(checker.get(190));
        Assertions.assertTrue(checker.get(100));
        Assertions.assertTrue(checker.get(48));
        Assertions.assertTrue(checker.get(16));
        Assertions.assertFalse(checker.get(32));

        checker.set(49);
        Assertions.assertTrue(checker.get(49));
        Assertions.assertFalse(checker.get(48));
        Assertions.assertFalse(checker.get(16));
        Assertions.assertTrue(checker.get(12));
        Assertions.assertFalse(checker.get(191));
        Assertions.assertTrue(checker.get(190));
        Assertions.assertTrue(checker.get(100));
        Assertions.assertFalse(checker.get(32));
    }

    @Test
    public void testOnes() {
        SequenceNumChecker checker = new SequenceNumChecker(128);
        checker.setAll();

        checker.set(31);
        Log.info(checker.toString());
    }

    @Test
    public void testMulti() {
        SequenceNumChecker checker = new SequenceNumChecker(192);

        checker.set(asBits("00000000 11111111 10101010"), 0, 32);
        Log.info(DebugUtils.bitString(checker.bits[0], 64) + " - " + DebugUtils.bitString(checker.bits[1], 64) + " - " + DebugUtils.bitString(checker.bits[2], 64));

        checker.set(asBits("00000000 11111111 10101010"), 48, 32);
        Log.info(DebugUtils.bitString(checker.bits[0], 64) + " - " + DebugUtils.bitString(checker.bits[1], 64) + " - " + DebugUtils.bitString(checker.bits[2], 64));

        checker.set(asBits("00000000 10110101 11111111"), 96, 32);
        Log.info(DebugUtils.bitString(checker.bits[0], 64) + " - " + DebugUtils.bitString(checker.bits[1], 64) + " - " + DebugUtils.bitString(checker.bits[2], 64));

        checker.set(asBits("00000000 10110101 11111111"), 150, 32);
        Log.info(DebugUtils.bitString(checker.bits[0], 64) + " - " + DebugUtils.bitString(checker.bits[1], 64) + " - " + DebugUtils.bitString(checker.bits[2], 64));

        checker.set(asBits("00000000 00000000 00000001"), 0, 32);
        Log.info(DebugUtils.bitString(checker.bits[0], 64) + " - " + DebugUtils.bitString(checker.bits[1], 64) + " - " + DebugUtils.bitString(checker.bits[2], 64));

        checker.set(asBits("00000001 00000001 00000001"), 48, 32);
        Log.info(DebugUtils.bitString(checker.bits[0], 64) + " - " + DebugUtils.bitString(checker.bits[1], 64) + " - " + DebugUtils.bitString(checker.bits[2], 64));

        checker.set(asBits("00000000 11111111"), 16, 15);
        Log.info(DebugUtils.bitString(checker.bits[0], 64) + " - " + DebugUtils.bitString(checker.bits[1], 64) + " - " + DebugUtils.bitString(checker.bits[2], 64));

        checker.set(asBits("11111110 11111111"), 16, 12);
        Log.info(DebugUtils.bitString(checker.bits[0], 64) + " - " + DebugUtils.bitString(checker.bits[1], 64) + " - " + DebugUtils.bitString(checker.bits[2], 64));
    }

    @Test
    public void testShift() {
        SequenceNumChecker checker = new SequenceNumChecker(192);
        checker.set(asBits("00000000 11111111 10101010"), 0, 24);
        checker.set(asBits("01010101 11111111"), 64, 16);
        Log.info(checker.toString());
        checker.shiftDown(12);
        Log.info(checker.toString());
        Log.info(DebugUtils.bitString(checker.getBitMask(52, 64), 64));
    }

    private long asBits(String s) {
        long r = 0;
        for (int i = s.length()-1; i >= 0; --i) {
            char c = s.charAt(i);
            if (c == ' ') continue;
            r = (r << 1) | (s.charAt(i) == '0' ? 0 : 1);
        }
        return r;
    }

    @Test
    public void numbers() {
//        for (int i = 0; i < 64; ++i) {
//            Log.info("" + i + " ? " + (63 - (i & 0x3F)) + " ? " + (~i & 0x3F));
//        }
        int i = -1;
//        Log.info(DebugUtils.bitString(i & 0xFFFFFFFFL, 64));
        Log.info(DebugUtils.bitString(-1L >>> 63, 64));
    }
}