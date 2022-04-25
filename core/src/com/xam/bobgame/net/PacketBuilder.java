package com.xam.bobgame.net;

import com.badlogic.gdx.math.MathUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class PacketBuilder {
    public ByteBuffer buffer;
    private ByteOrder order;

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
    }

    private static int makeInt(byte var0, byte var1, byte var2, byte var3) {
        return var0 << 24 | (var1 & 255) << 16 | (var2 & 255) << 8 | var3 & 255;
    }

    public int packInt(int i, int min, int max) {
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

    public int getInt(int min, int max) {
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
        return packInt(i, 0, ri-1);
    }

    public float getFloat(float min, float max, float res) {
        float rf = max - min;
        int ri = MathUtils.ceil(rf / res);
        int i = getInt(0, ri);
        return min + (rf * ((float) i / (float) ri));
    }


}
