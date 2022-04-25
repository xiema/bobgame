package com.xam.bobgame.net;

import com.badlogic.gdx.math.MathUtils;
import com.xam.bobgame.utils.DebugUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class PacketBuilder {
    public ByteBuffer buffer;
    private ByteOrder order;
    private long scratch = 0;
    private int scratchBits = 0;

    public PacketBuilder() {

    }

    public PacketBuilder(int size) {
        buffer = ByteBuffer.allocate(size);
        order = buffer.order();
    }

    public PacketBuilder(ByteBuffer buffer) {
        this.buffer = buffer;
        order = buffer.order();
    }

    public void setBuffer(ByteBuffer buffer) {
        this.buffer = buffer;
        order = buffer.order();
        scratch = 0;
        scratchBits = 0;
    }

    public boolean hasRemaining() {
        return buffer.hasRemaining();
    }

    public byte unpackByte() {
        return (byte) unpackIntBits(8, 0);
    }

    public int packByte(byte b) {
        packIntBits((b & 255), 8, 0);
        return 8;
    }

    private static int makeInt(byte var0, byte var1, byte var2, byte var3) {
        return var0 << 24 | (var1 & 255) << 16 | (var2 & 255) << 8 | var3 & 255;
    }

    private void getBitsB(int bits) {
        int byteCount = (bits + 7) / 8;
        scratchBits += byteCount * 8;
        if (scratchBits > 64) {
            DebugUtils.error("PacketBuilder", "gigtBitsB: Tried to get too many bits");
            return;
        }
        while (byteCount-- > 0) {
            scratch = (scratch << 8) | (buffer.get() & 255L);
        }
    }

    private void getBitsL(int bits) {
        int byteCount = (bits + 7) / 8;
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
//        if (bits == 32) return 4294967295L;
//        if (bits < 32) {
//            if (bits == 16) return ;
//            if (bits < 16) {
//                if (bits == 8) return ;
//                if (bits < 8) {
//                    if (bits == 4) return 15L;
//                    if (bits < 4) {
//                        if (bits == 2) return 3L;
//                        if (bits < 2) return bits == 1 ? 1L : 0L;
//                        else return 7L;
//                    }
//                    else {
//                        if (bits == 6) return 63L;
//                        return bits == 5 ? 31L : 127L;
//                    }
//                }
//                else {
//
//                }
//            }
//            else {
//
//            }
//        }
//        else {
//
//        }
        return (1L << (bits + 1)) - 1L;
    }

    public int flush() {
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
        }
        else {
            while (byteCount-- > 1) {
                buffer.put((byte) (scratch & 255));
                scratch >>= 8;
            }
        }
        scratchBits = 0;

        return r;
    }

    public void clear() {
        scratch = 0;
        scratchBits = 0;
    }

    public int unpackIntBits(int packBits, int min) {
        int i = min;

        if (order == ByteOrder.BIG_ENDIAN) {
            if (scratchBits < packBits) getBitsB(packBits);
            i += (int) (scratch >> (scratchBits - packBits));
            scratchBits -= packBits;
            scratch &= ((1L << scratchBits) - 1L);
        }
        else {
            if (scratchBits < packBits) getBitsL(packBits);
            i += (int) (scratch & mask(packBits));
            scratch >>= packBits;
            scratchBits -= packBits;
        }

        return i;
    }

    public int unpackInt(int min, int max) {
        int r = max - min;
        int packBits = r < 0 ? 32 : calcBits(r);
        return unpackIntBits(packBits , min);
    }

    public void packIntBits(int i, int packBits, int min) {
        int p;
        i -= min;
        if (order == ByteOrder.BIG_ENDIAN) {
            if (scratchBits + packBits <= 64) {
                scratch = (scratch << packBits) | i;
                scratchBits += packBits;
            }
            else {
                p = 64 - scratchBits;
                scratchBits = packBits - p;
                buffer.putLong((scratch << p) | (i >> scratchBits));
                scratch = i & mask(scratchBits);
            }
        }
        else {
            if (scratchBits + packBits <= 64) {
                scratch |= (i & 4294967295L) << packBits;
                scratchBits += packBits;
            }
            else {
                buffer.putLong(scratch | ((i & 4294967295L) << scratchBits));
                scratchBits += packBits - 64;
                scratch = i >> scratchBits;
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
        packIntBits(i, packBits , min);
        return packBits;
    }

    public int packInt2(int i, int min, int max) {
        int r = max - min + 1;
        if (r < 0 || r >= 16777216) {
            buffer.putInt(i);
            return 4;
        }
        i -= min;

        if (this.order != ByteOrder.BIG_ENDIAN) {
            if (r < 256) {
                buffer.put((byte) (i & 255));
                return 1;
            } else if (r < 65536) {
                buffer.put((byte) (i & 255));
                buffer.put((byte) (i >> 8 & 255));
                return 2;
            } else {
                buffer.put((byte) (i & 255));
                buffer.put((byte) (i >> 8 & 255));
                buffer.put((byte) (i >> 16 & 255));
                return 3;
            }
        }
        else {
            if (r < 256) {
                buffer.put((byte) (i & 255));
                return 1;
            } else if (r < 65536) {
                buffer.put((byte) ((i >> 8) & 255));
                buffer.put((byte) (i & 255));
                return 2;
            } else {
                buffer.put((byte) ((i >> 16) & 255));
                buffer.put((byte) ((i >> 8) & 255));
                buffer.put((byte) (i & 255));
                return 3;
            }
        }
    }

    public int unpackInt2(int min, int max) {
        int r = max - min + 1;
        if (r < 0 || r >= 16777216) return buffer.getInt();
        int i = 0;

        if (this.order != ByteOrder.BIG_ENDIAN) {
            if (r < 256) {
                i |= buffer.get() & 255;
            } else if (r < 65536) {
                i |= (buffer.get() & 255) | ((buffer.get() & 255) << 8);
            } else {
                i |= (buffer.get() & 255) | ((buffer.get() & 255) << 16);
            }
        }
        else {
            if (r < 256) {
                i |= buffer.get() & 255;
            } else if (r < 65536) {
                i |= ((buffer.get() & 255) << 8) | (buffer.get() & 255);
            } else {
                i |= ((buffer.get() & 255) << 16) | ((buffer.get() & 255) << 8) | (buffer.get() & 255);
            }
        }

        return i + min;
    }

    public int packFloat(float f, float min, float max, float res) {
        float rf = max - min;
        int ri = MathUtils.ceil(rf / res);
        float fn = MathUtils.clamp((f - min) / rf, 0f, 1f);
        int i = MathUtils.floor(fn * ri + 0.5f);
        return packInt(i, 0, ri);
    }

    public float unpackFloat(float min, float max, float res) {
        float rf = max - min;
        int ri = MathUtils.ceil(rf / res);
        int i = unpackInt(0, ri);
        return min + (rf * ((float) i / (float) ri));
    }


}
