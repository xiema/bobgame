package com.xam.bobgame.utils;

import com.esotericsoftware.minlog.Log;

import java.util.Arrays;

/**
 * A bitset similar to {@link com.badlogic.gdx.utils.Bits}, but with operations operating on ranges of bits.
 */
public class Bits2 {

    long[] bits;

    int size;
    int halfSize;

    public Bits2(int size) {
        this.size = size;
        this.halfSize = size / 2;
        bits = new long[(size + 63 >>> 6)];
    }

    public boolean get(int i) {
        if (!checkCapacity(i)) {
            Log.warn("Bits2", "Attempted get " + i + " but size is only " + size);
            return false;
        }
        return (bits[(i >>> 6) % bits.length] & (1L << (i & 0x3F))) != 0L;
    }

    public void set(int i) {
        if (!checkCapacity(i)) {
            Log.warn("Bits2", "Attempted set " + i + " but size is only " + size);
            return;
        }
        bits[i >>> 6] |= 1L << (i & 0x3F);
    }

    public void unset(int i) {
        if (!checkCapacity(i)) {
            Log.warn("Bits2", "Attempted unset " + i + " but size is only " + size);
            return;
        }
        bits[i >>> 6] &= ~(1L << (i & 0x3F));
    }

    public boolean getAndSet(int i) {
        if (!checkCapacity(i)) {
            Log.warn("Bits2", "Attempted getAndSet " + i + " but size is only " + size);
            return false;
        }
        int word = i >>> 6;
        long oldBits = bits[word];
        bits[word] |= 1L << (i & 0x3F);
        return bits[word] == oldBits;
    }

    public void clear() {
        Arrays.fill(bits, 0);
    }

    public void setAll() {
        Arrays.fill(bits, -1);
    }

    /**
     * Sets a range of bits by bitwise OR
     * @param i A long value representing the bits to be set
     * @param offset Starting offset in the bitset
     * @param length Number of bits to be set
     */
    public void set(long i, int offset, int length) {
        if (!checkCapacity(offset + length - 1)) {
            Log.warn("Bits2", "Attempted set " + length + " bits from " + offset + " but size is only " + size);
            return;
        }
        int word = offset >>> 6;
        long mask = length == 64 ? -1 : ((1L << length) - 1);
        bits[word] |= (i << (offset & 0x3F)) & (mask << (offset & 0x3F));
        if ((offset & 0x3F) != 0) bits[(word + 1) % bits.length] |= (i >>> (-offset & 0x3F)) & (mask >>> (-offset & 0x3F));
    }

    /**
     * Sets a range of 64 bits by bitwise OR
     * @param i A long value representing the bits to be set
     * @param offset Starting offset in the bitset
     */
    public void set(long i, int offset) {
        set(i, offset, 64);
    }

    /**
     * Shifts bits in the bitset down by the specified number, replacing the topmost bits by zeros.
     * @param n Number of bits to shift
     */
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

    /**
     * Creates a long value representing bits in the bitset in a specified range.
     * @param offset Starting offset in the bitset
     * @param length Number of bits
     * @return Long value representing the bits
     */
    public long getBitMask(int offset, int length) {
        if (!checkCapacity(offset + length - 1)) {
            Log.warn("Bits2", "Attempted set " + length + " bits from " + offset + " but size is only " + size);
        }
        int word = offset >>> 6;
        long mask = length == 64 ? -1 : ((1L << length) - 1);
        return ((bits[word] >>> (offset & 0x3F)) | (bits[(word + 1) % bits.length] << (-offset & 0x3F))) & mask;
    }

    public void copyTo(Bits2 other) {
        if (other.bits == null || other.bits.length < bits.length) other.bits = new long[bits.length];
        System.arraycopy(bits, 0, other.bits, 0, bits.length);
        other.size = size;
        other.halfSize = halfSize;
    }

    /**
     * Returns true if any bits are set.
     */
    public boolean anySet() {
        for (int i = 0; i < bits.length; ++i) {
            if (bits[i] > 0) return true;
        }
        return false;
    }

    /**
     * Performs bitwise OR on this bitset with another bitset.
     */
    public void or(Bits2 other) {
        for (int i = 0; i < bits.length; ++i) {
            bits[i] |= other.bits[i];
        }
    }

    private boolean checkCapacity (int i) {
        return i < size;
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
