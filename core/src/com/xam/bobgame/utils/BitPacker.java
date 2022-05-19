package com.xam.bobgame.utils;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.minlog.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * A bit packer (or bit stuffer) that packs bits into a ByteBuffer. Has functions for packing int, long, float,
 * boolean, and byte values.
 */
public class BitPacker {
    private ByteBuffer buffer;

    /**
     * Temporary storage for bits to be packed
     */
    private long scratch = 0;
    /**
     * Number of bits currently stored in scratch
     */
    private int scratchBits = 0;
    /**
     * Total number of bits packed or unpacked
     */
    private int totalBits = 0;

    /**
     * True when writing to buffer
     */
    private boolean write = false;

    public BitPacker() {

    }

    public BitPacker(ByteBuffer buffer) {
        setBuffer(buffer);
    }

    public int getTotalBits() {
        return totalBits;
    }

    public int getTotalBytes() {
        return (totalBits + 7) / 8;
    }

    public void setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
        clear();
    }

    public void setReadMode() {
        write = false;
    }

    public void setWriteMode() {
        write = true;
    }

    public boolean isReadMode() {
        return !write;
    }

    public boolean isWriteMode() {
        return write;
    }

    public boolean hasRemaining() {
        return buffer.remaining() > (scratchBits + 7) / 8;
    }

    /**
     * In write mode, packs the specified int and returns it. In read mode, ignores the specified int and unpacks an
     * int from the buffer, returning it.
     */
    public int readInt(int i, int min, int max) {
        if (write) {
            packInt(i, min, max);
            return i;
        }
        else {
            return unpackInt(min, max);
        }
    }

    /**
     * In write mode, packs the specified float and returns it. In read mode, ignores the specified float and unpacks an
     * float from the buffer, returning it.
     */
    public float readFloat(float f, float min, float max, float res) {
        if (write) {
            packFloat(f, min, max, res);
            return f;
        }
        else {
            return unpackFloat(min, max, res);
        }
    }

    /**
     * In write mode, packs the specified byte and returns it. In read mode, ignores the specified byte and unpacks an
     * byte from the buffer, returning it.
     */
    public byte readByte(byte b) {
        if (write) {
            packByte(b);
            return b;
        }
        else {
            return unpackByte();
        }
    }

    /**
     * In write mode, packs the specified boolean and returns it. In read mode, ignores the specified boolean and unpacks an
     * boolean from the buffer, returning it.
     */
    public boolean readBoolean(boolean b) {
        if (write) {
            packInt(b ? 1 : 0, 0, 1);
            return b;
        }
        else {
            return unpackInt(0, 1) == 1;
        }
    }

    public byte unpackByte() {
        totalBits += 8;
        return (byte) unpackIntBits(8, 0);
    }

    public int packByte(byte b) {
        packIntBits((b & 0xFF), 8, 0);
        return 8;
    }

    /**
     * Gets additional bits from the buffer. Big Endian.
     */
    private void getBitsB(int bits) {
        int byteCount = (bits - scratchBits + 7) / 8;
        scratchBits += byteCount * 8;
        if (scratchBits > 64) {
            Log.error("PacketBuilder", "getBitsB: Tried to get too many bits");
            return;
        }
        while (byteCount-- > 0) {
            if (!buffer.hasRemaining()) {
                Log.warn("PacketBuilder", "Buffer underflow (" + byteCount + ")");
                scratch <<= 8;
            } else {
                scratch = (scratch << 8) | (buffer.get() & 0xFFL);
            }
        }
    }

    /**
     * Gets additional bits from the buffer. Little Endian.
     */
    private void getBitsL(int bits) {
        int byteCount = (bits - scratchBits + 7) / 8;
        scratchBits += byteCount * 8;
        if (scratchBits > 64) {
            Log.error("PacketBuilder", "getBitsL: Tried to get too many bits");
            return;
        }
        int p = 0;
        while (byteCount-- > 0) {
            p += 8;
            scratch = scratch | ((buffer.get() & 0xFFL) << p);
        }
    }

    private static final long[] masks = new long[64];
    static {
        long m = 1L;
        for (int i = 0; i < masks.length; ++i) {
            masks[i] = m - 1L;
            m <<= 1;
        }
    }

    /**
     * Stores all remaining bits in the scratch into the buffer, padded to the nearest byte.
     * @param rewind Whether to rewind the buffer after flushing
     * @return The number of bits padded
     */
    public int flush(boolean rewind) {
        int byteCount = (scratchBits + 7) / 8;
        int p = byteCount * 8;
        int r = p - scratchBits;

        if (buffer.order() == ByteOrder.BIG_ENDIAN) {
            scratch <<= (64 - scratchBits) % 8;
            while (byteCount-- > 0) {
                p -= 8;
                buffer.put((byte) ((scratch >> p) & 0xFF));
            }
            scratch = 0;
        } else {
            while (byteCount-- > 0) {
                buffer.put((byte) (scratch & 0xFF));
                scratch >>= 8;
            }
        }
        scratchBits = 0;

        if (rewind) buffer.rewind();

        return r;
    }

    public void rewind() {
        buffer.rewind();
    }

    public void clear() {
        scratch = 0;
        scratchBits = 0;
        totalBits = 0;
    }

    public int padToLong() {
        int padding = 64 - scratchBits;
        if (padding != 0) packIntBits(0, padding, 0);
        return padding;
    }

    public int skipToLong() {
        int padding = ((-totalBits % 64) + 64) % 64;
        if (padding != 0) unpackIntBits(padding, 0);
        return padding;
    }

    public int padToNextByte() {
        int padding = ((-scratchBits % 8) + 8) % 8;
        packIntBits(0, padding, 0);
        return padding;
    }

    public int skipToNextByte() {
        int padding = ((-totalBits % 8) + 8) % 8;
        unpackIntBits(padding, 0);
        return padding;
    }

    public int unpackIntBits(int packBits, int min) {
        int i = min;

        if (buffer.order() == ByteOrder.BIG_ENDIAN) {
            if (scratchBits < packBits) getBitsB(packBits);
            i += (int) (scratch >> (scratchBits - packBits));
            scratchBits -= packBits;
            scratch &= ((1L << scratchBits) - 1L);
        } else {
            if (scratchBits < packBits) getBitsL(packBits);
            i += (int) (scratch & masks[packBits]);
            scratch >>= packBits;
            scratchBits -= packBits;
        }

        totalBits += packBits;

        return i;
    }

    public long unpackBits(int packBits, long min) {
        long i = min;

        if (buffer.order() == ByteOrder.BIG_ENDIAN) {
            if (scratchBits < packBits) getBitsB(packBits);
            i += (int) (scratch >> (scratchBits - packBits));
            scratchBits -= packBits;
            scratch &= ((1L << scratchBits) - 1L);
        } else {
            if (scratchBits < packBits) getBitsL(packBits);
            i += (int) (scratch & masks[packBits]);
            scratch >>= packBits;
            scratchBits -= packBits;
        }

        totalBits += packBits;

        return i;
    }

    public int unpackInt(int min, int max) {
        int r = max - min;
        int packBits = r < 0 ? 32 : calcBits(r);
        return unpackIntBits(packBits, min);
    }

    public int unpackInt() {
        return unpackIntBits(32, 0);
    }


    public void packIntBits(int i, int bitCount, int min) {
        packBits(((i - min) & 0xFFFFFFFFL), bitCount, 0);
    }

    public void packBits(long i, int bitCount, int min) {
        int p;
        i -= min;
        if (buffer.order() == ByteOrder.BIG_ENDIAN) {
            if (scratchBits + bitCount <= 64) {
                scratch = (scratch << bitCount) | i;
                scratchBits += bitCount;
            } else {
                p = 64 - scratchBits;
                scratchBits = bitCount - p;
                buffer.putLong((scratch << p) | (i >> scratchBits));
                scratch = i & masks[scratchBits];
            }
        } else {
            if (scratchBits + bitCount <= 64) {
                scratch |= i << bitCount;
                scratchBits += bitCount;
            } else {
                buffer.putLong(scratch | ((i & 0xFFFFFFFFL) << scratchBits));
                scratchBits += bitCount - 64;
                scratch = i >> scratchBits;
            }
        }

        totalBits += bitCount;
    }

    public int packBytes(ByteBuffer in, int l) {
        int count = l;
        if (scratchBits % 8 == 0) {
            flush(false);
            while (count-- > 0) {
                buffer.put(in.get());
                totalBits += 8;
            }
        }
        else {
            while (count-- > 0) {
                packByte(in.get());
            }
        }
        return l;
    }

    public void unpackBytes(ByteBuffer out, int l) {
        if (scratchBits % 8 == 0) {
            while (scratchBits > 0 && l > 0) {
                out.put(unpackByte());
                l--;
            }
            while (l > 0) {
                out.put(buffer.get());
                l--;
            }
        }
    }

    private static int calcBits(long l) {
        int r = 0;
        while (l > 0) {
            l >>= 1;
            r++;
        }
        return r;
    }

    public int packInt(int i, int min, int max) {
        int r = max - min;
        int bitCount = r < 0 ? 32 : calcBits(r);
        packIntBits(i, bitCount, min);
        return bitCount;
    }

    public int packInt(int i) {
        packBits(i & 0xFFFFFFFFL, 32, 0);
        return 32;
    }

    public int packFloat(float f, float min, float max, float res) {
        float rf = max - min;
        int ri = MathUtils.ceil(rf / res);
        if (f == min) return packInt(0, 0, ri);
        if (f == max) return packInt(ri, 0, ri);
        float fn = MathUtils.clamp((f - min) / rf, 0f, 1f);
        int i = MathUtils.floor(fn * ri + 0.5f);
        return packInt(i, 0, ri);
    }

    public float unpackFloat(float min, float max, float res) {
        float rf = max - min;
        int ri = MathUtils.ceil(rf / res);
        int i = unpackInt(0, ri);
        return min + ((rf * (float) i) / (float) ri);
    }

    public int packFloat(float f) {
        return packInt(Float.floatToRawIntBits(f));
    }

    public float unpackFloat() {
        return Float.intBitsToFloat(unpackInt());
    }

    public void debugRemaining() {
        int i = buffer.position();
        Log.info(DebugUtils.bytesHex(buffer, i, buffer.limit()));
        buffer.position(i);
    }

    public void debugCurrent() {
        int i = buffer.position();
        Log.info(DebugUtils.bytesHex(buffer, 0, buffer.position()) + (scratchBits > 0 ? Long.toHexString(scratch) : ""));
    }
}
