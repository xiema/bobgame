package com.xam.bobgame.graphics.animators;


import com.badlogic.ashley.core.Engine;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.BitPacker;

public class BlinkAnimator<T extends Animated> extends Animator<T> {

    float rate, low, high;

    public BlinkAnimator() {
    }

    public void set(T object, float rate, float low, float high) {
        this.object = object;
        this.rate = rate;
        this.low = low;
        this.high = high;
    }

    @Override
    protected void animate() {
        object.getDrawTint().mul(1, 1, 1, (((int) (accumulator / rate)) % 2) == 1 ? low : high);
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        rate = packer.readFloat(rate, 0, 7, NetDriver.RES_GRAPHICS_BLINK_FREQ);
        low = packer.readFloat(low, 0, 1, NetDriver.RES_COLOR);
        high = packer.readFloat(high, 0, 1, NetDriver.RES_COLOR);
        return 0;
    }
}
