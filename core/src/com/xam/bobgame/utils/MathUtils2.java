package com.xam.bobgame.utils;

public class MathUtils2 {

    public static float quantize(float f, float res) {
        return Math.round(((double) f) / res) * res;
    }
}
