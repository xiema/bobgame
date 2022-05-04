package com.xam.bobgame.utils;

import com.badlogic.gdx.utils.Bits;
import com.esotericsoftware.minlog.Log;

import java.util.Arrays;

public class SequenceNumChecker {

    long[] bits;

//    final Bits bits;
    final int size;
    final int halfSize;

    int high = 0;

    public SequenceNumChecker(int size) {
        this.size = size;
        this.halfSize = size / 2;
        bits = new long[(size + 63 >>> 6)];
//        high = halfSize;
    }

    public boolean get(int i) {
        return (bits[(i >>> 6) % bits.length] & (1L << (i & 0x3F))) != 0L;
    }

    public boolean gtWrapped(int i, int j) {
        return ((i < j && j - i > halfSize) || (i > j && i - j <= halfSize));
    }

    public void set(int i) {
        if (gtWrapped(i, high)) {
            zeroBits(high, i);
            high = i;
        }
        bits[i >>> 6] |= 1L << (i & 0x3F);
    }

    public void unset(int i) {
        bits[i >>> 6] &= ~(1L << (i & 0x3F));
    }

    public boolean getAndSet(int i) {
        i %= size;
        int word = i >>> 6;
        long oldBits = bits[word];
        bits[word] |= 1L << (i & 0x3F);

        if (gtWrapped(i, high))  {
            zeroBits(high, i);
            high = i;
            return false;
        }

        return bits[word] == oldBits;
    }

    public void clear() {
        Arrays.fill(bits, 0);
    }

    public void reset() {
        clear();
        high = 0;
    }

    public void setAll() {
        Arrays.fill(bits, -1);
    }

    public void set(long i, int offset, int length) {
        int end = (offset + length) % size;
        if (gtWrapped(end, high)) {
            zeroBits(high, end);
            high = end;
        }
        int word = offset >>> 6;
        long mask = length == 64 ? -1 : ((1L << length) - 1);
        bits[word] |= (i << (offset & 0x3F)) & (mask << (offset & 0x3F));
        if ((offset & 0x3F) != 0) bits[(word + 1) % bits.length] |= (i >>> (-offset & 0x3F)) & (mask >>> (-offset & 0x3F));
    }

    public void set(long i, int offset) {
        set(i, offset, 64);
    }

    public void shiftDown(int n) {
        int i = 0, j = (n >>> 6);
        if (j < bits.length) {
            long lower = bits[j++] >>> (n & 0x3F);
            while (j < bits.length) {
                bits[i++] = lower | (bits[j] << (-n & 0x3F));
                lower = bits[j++] >>> (n & 0x3F);
            }
        }
        else {
            clear();
        }
    }

    private void zeroBits(int i, int j) {
//        Log.info("zero i=" + i + " j=" + j);
        int w1 = i >>> 6;
        int w2 = j >>> 6;

//        Log.info("A1 bits[" + w1 + "]=" + DebugUtils.bitString(bits[w1], 64));
//        Log.info("B1 bits[" + w2 + "]=" + DebugUtils.bitString(bits[w2], 64));
        if (w1 == w2) {
            bits[w1] &= (-1L >>> (~i & 0x3F)) | (-1L << (j & 0x3F));
//            Log.info("A2 bits[" + w1 + "]=" + DebugUtils.bitString(bits[w1], 64));
        }
        else {
//            Log.info("A1 " + DebugUtils.bitString((-1L >>> (63 - (i & 0x3F))), 64));
            bits[w1] &= -1L >>> (~i & 0x3F);
            bits[w2] &= (-1L << (j & 0x3F));
        }
//        Log.info("A1 bits[" + w1 + "]=" + DebugUtils.bitString(bits[w1], 64));
//        Log.info("B1 bits[" + w2 + "]=" + DebugUtils.bitString(bits[w2], 64));
        if (j - i > 64 || i - j > 64) {
            for (w1 = (w1 + 1) % bits.length; w1 != w2; w1 = (w1 + 1) % bits.length) {
//                Log.info("C1 bits[" + w1 + "]=" + DebugUtils.bitString(bits[w1], 64));
                bits[w1] = 0;
//                Log.info("C2 bits[" + w1 + "]=" + DebugUtils.bitString(bits[w1], 64));
            }
        }
    }

    public int getHigh() {
        return high;
    }

    public long getBitMask(int offset, int length) {
        int word = offset >>> 6;
        long mask = length == 64 ? -1 : ((1L << length) - 1);
        return ((bits[word] >>> (offset & 0x3F)) | (bits[(word + 1) % bits.length] << (-offset & 0x3F))) & mask;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bits.length; ++i) {
            sb.append(DebugUtils.bitString(bits[i], 64));
            sb.append(": ");
        }
        return sb.toString();
    }
}
