package com.xam.bobgame.utils;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.DebugUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BitPacker {
    private ByteBuffer buffer;
    private ByteOrder order;
    private long scratch = 0;
    private int scratchBits = 0;
    private int totalBits = 0;

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
        order = buffer.order();
        clear();
    }

    public boolean hasRemaining() {
        return buffer.remaining() > (scratchBits + 7) / 8;
    }

    public byte unpackByte() {
        totalBits += 8;
        return (byte) unpackIntBits(8, 0);
    }

    public int packByte(byte b) {
        packIntBits((b & 255), 8, 0);
        totalBits += 8;
        return 8;
    }

    private void getBitsB(int bits) {
        int byteCount = (bits - scratchBits + 7) / 8;
        scratchBits += byteCount * 8;
        if (scratchBits > 64) {
            Log.error("PacketBuilder", "gigtBitsB: Tried to get too many bits");
            return;
        }
        while (byteCount-- > 0) {
            if (!buffer.hasRemaining()) {
                Log.warn("PacketBuilder", "Buffer underflow (" + byteCount + ")");
                scratch <<= 8;
            } else {
                scratch = (scratch << 8) | (buffer.get() & 255L);
            }
        }
    }

    private void getBitsL(int bits) {
        int byteCount = (bits - scratchBits + 7) / 8;
        scratchBits += byteCount * 8;
        if (scratchBits > 64) {
            Log.error("PacketBuilder", "gigtBitsL: Tried to get too many bits");
            return;
        }
        int p = 0;
        while (byteCount-- > 0) {
            p += 8;
            scratch = scratch | ((buffer.get() & 255L) << p);
        }
    }

    private long mask(int bits) {
        return (1L << bits) - 1L;
    }

    public int flush(boolean rewind) {
        int byteCount = (scratchBits + 7) / 8;
        int p = byteCount * 8;
        int r = p - scratchBits;

        if (order == ByteOrder.BIG_ENDIAN) {
            scratch <<= (64 - scratchBits) % 8;
            while (byteCount-- > 0) {
                p -= 8;
                buffer.put((byte) ((scratch >> p) & 255));
            }
            scratch = 0;
        } else {
            while (byteCount-- > 0) {
                buffer.put((byte) (scratch & 255));
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

        if (order == ByteOrder.BIG_ENDIAN) {
            if (scratchBits < packBits) getBitsB(packBits);
//            i += (int) ((scratch >> (scratchBits - packBits)) & ((1L << (packBits + 1)) - 1));
            i += (int) (scratch >> (scratchBits - packBits));
            scratchBits -= packBits;
            scratch &= ((1L << scratchBits) - 1L);
        } else {
            if (scratchBits < packBits) getBitsL(packBits);
            i += (int) (scratch & mask(packBits));
            scratch >>= packBits;
            scratchBits -= packBits;
        }

        totalBits += packBits;

        return i;
    }

    public long unpackBits(int packBits, long min) {
        long i = min;

        if (order == ByteOrder.BIG_ENDIAN) {
            if (scratchBits < packBits) getBitsB(packBits);
            i += (int) (scratch >> (scratchBits - packBits));
            scratchBits -= packBits;
            scratch &= ((1L << scratchBits) - 1L);
        } else {
            if (scratchBits < packBits) getBitsL(packBits);
            i += (int) (scratch & mask(packBits));
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


    public void packIntBits(int i, int packBits, int min) {
        int p;
        long l = ((i - min) & 0xFFFFFFFFL);
//        i -= min;
        if (order == ByteOrder.BIG_ENDIAN) {
            if (scratchBits + packBits <= 64) {
                scratch = (scratch << packBits) | l;
                scratchBits += packBits;
            } else {
                p = 64 - scratchBits;
                scratchBits = packBits - p;
                buffer.putLong((scratch << p) | (l >> scratchBits));
//                message.length += 8;
                scratch = l & mask(scratchBits);
            }
        } else {
            if (scratchBits + packBits <= 64) {
                scratch |= (l & 4294967295L) << packBits;
                scratchBits += packBits;
            } else {
                buffer.putLong(scratch | ((l & 4294967295L) << scratchBits));
//                message.length += 8;
                scratchBits += packBits - 64;
                scratch = l >> scratchBits;
            }
        }

        totalBits += packBits;
    }

    public void packBits(long i, int packBits, int min) {
        int p;
        i -= min;
        if (order == ByteOrder.BIG_ENDIAN) {
            if (scratchBits + packBits <= 64) {
                scratch = (scratch << packBits) | i;
                scratchBits += packBits;
            } else {
                p = 64 - scratchBits;
                scratchBits = packBits - p;
                buffer.putLong((scratch << p) | (i >> scratchBits));
                scratch = i & mask(scratchBits);
            }
        } else {
            if (scratchBits + packBits <= 64) {
                scratch |= i << packBits;
                scratchBits += packBits;
            } else {
                buffer.putLong(scratch | ((i & 4294967295L) << scratchBits));
                scratchBits += packBits - 64;
                scratch = i >> scratchBits;
            }
        }

        totalBits += packBits;
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
        byte b;
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
        int packBits = r < 0 ? 32 : calcBits(r);
        packIntBits(i, packBits, min);
        return packBits;
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
