package com.xam.bobgame.net;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.DebugUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BitPacker {
//    private Message message;
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
            DebugUtils.error("PacketBuilder", "gigtBitsB: Tried to get too many bits");
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
            DebugUtils.error("PacketBuilder", "gigtBitsL: Tried to get too many bits");
            return;
        }
        int p = 0;
        while (byteCount-- > 0) {
            p += 8;
            scratch = scratch | ((buffer.get() & 255L) << p);
        }
    }

    private long mask(int bits) {
        return (1L << (bits + 1)) - 1L;
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
//                message.length++;
            }
            scratch = 0;
        } else {
            while (byteCount-- > 0) {
                buffer.put((byte) (scratch & 255));
                scratch >>= 8;
//                message.length++;
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

    public int unpackIntBits(int packBits, int min) {
        int i = min;

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

    public void packIntBits(int i, int packBits, int min) {
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
//                message.length += 8;
                scratch = i & mask(scratchBits);
            }
        } else {
            if (scratchBits + packBits <= 64) {
                scratch |= (i & 4294967295L) << packBits;
                scratchBits += packBits;
            } else {
                buffer.putLong(scratch | ((i & 4294967295L) << scratchBits));
//                message.length += 8;
                scratchBits += packBits - 64;
                scratch = i >> scratchBits;
            }
        }

        totalBits += packBits;
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


}
