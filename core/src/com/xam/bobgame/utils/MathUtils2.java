package com.xam.bobgame.utils;

import com.badlogic.gdx.math.Interpolation;

public class MathUtils2 {

    public static float quantize(float f, float res) {
        return Math.round(((double) f) / res) * res;
    }

    public static final Interpolation mirror = new Interpolation() {
        @Override
        public float apply(float a) {
            float b = a % 1f;
            return b < 0.5 ? b * 2 : (1 - b) * 2;
        }
    };
}
