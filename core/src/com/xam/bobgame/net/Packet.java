package com.xam.bobgame.net;

import com.badlogic.gdx.math.MathUtils;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.utils.DebugUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;

public class Packet {
    private ByteBuffer byteBuffer;
    private CRC32 crc32 = new CRC32();
    private long crc = -1;
    private int length = 0;

    public Packet(int size) {
        byteBuffer = ByteBuffer.allocate(size);
    }

    public Packet(ByteBuffer byteBuffer) {
        this.byteBuffer = byteBuffer;
    }

    public long getCrc() {
        if (crc < 0) {
            crc32.reset();
            crc32.update(byteBuffer.array(), 0, length);
            crc = crc32.getValue();
        }
        return crc;
    }

    public int getLength() {
        return length;
    }

    public void copyTo(ByteBuffer out) {
        int i = length;
        while (i-- > 0) {
            out.put(byteBuffer.get());
        }
        byteBuffer.rewind();
    }

    public void copyTo(Packet out) {
        out.set(byteBuffer, length);
        byteBuffer.rewind();
    }

    public void set(ByteBuffer in, int length) {
        byteBuffer.clear();
        this.length = length;
        while (length-- > 0) {
            byteBuffer.put(in.get());
        }
        byteBuffer.flip();
        crc = -1;
    }

    public void clear() {
        byteBuffer.clear();
        length = 0;
        crc = -1;
    }

    @Override
    public String toString() {
        return DebugUtils.intHex((int) getCrc()) + DebugUtils.intHex(length) + DebugUtils.bytesHex(byteBuffer.array());
    }

    public boolean equals(Packet other) {
        for (int i = 0; i < length; ++i) {
            if (byteBuffer.get(i) != other.byteBuffer.get(i)) {
                return false;
            }
        }
        return true;
    }


    public static class PacketBuilder {
        private Packet packet;
        private ByteBuffer buffer;
        private ByteOrder order;
        private long scratch = 0;
        private int scratchBits = 0;
        private int totalBits = 0;

        public PacketBuilder() {

        }

        public PacketBuilder(Packet packet) {
            setPacket(packet);
        }

        public int getTotalBits() {
            return totalBits;
        }

        public void setPacket(Packet packet) {
            this.packet = packet;
            buffer = packet.byteBuffer;
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
                    packet.length++;
                }
                scratch = 0;
            }
            else {
                while (byteCount-- > 0) {
                    buffer.put((byte) (scratch & 255));
                    scratch >>= 8;
                    packet.length++;
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
            }
            else {
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
                    packet.length += 8;
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
                    packet.length += 8;
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
            packIntBits(i, packBits , min);
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
            return min + (rf * ((float) i / (float) ri));
        }


    }
}
