package com.xam.bobgame.components;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.utils.BitPacker;

public class Component2 implements Component {

    protected int readInt(BitPacker packer, int i, int min, int max, boolean write) {
        if (write) {
            packer.packInt(i, min, max);
            return i;
        }
        else {
            return packer.unpackInt(min, max);
        }
    }

    protected float readFloat(BitPacker packer, float f, float min, float max, float res, boolean write) {
        if (write) {
            packer.packFloat(f, min, max, res);
            return f;
        }
        else {
            return packer.unpackFloat(min, max, res);
        }
    }

    protected byte readByte(BitPacker packer, byte b, boolean write) {
        if (write) {
            packer.packByte(b);
            return b;
        }
        else {
            return packer.unpackByte();
        }
    }

    public void read(BitPacker packer, Engine engine, boolean write){

    }

    public int getTypeIndex() {
        return -1;
    }
}
