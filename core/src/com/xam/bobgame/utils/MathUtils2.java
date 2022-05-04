package com.xam.bobgame.utils;

import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector2;

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

    public static float vectorToAngle (Vector2 vector) {
        return (float)Math.atan2(-vector.x, vector.y) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;
    }

    public static float vectorToAngle (float x, float y) {
        return (float)Math.atan2(-x, y) * com.badlogic.gdx.math.MathUtils.radiansToDegrees;
    }

    public static float vectorToAngleRad (Vector2 vector) {
        return (float)Math.atan2(-vector.x, vector.y);
    }

    public static Vector2 angleToVector (Vector2 outVector, float angle) {
        outVector.x = -com.badlogic.gdx.math.MathUtils.sinDeg(angle);
        outVector.y = com.badlogic.gdx.math.MathUtils.cosDeg(angle);
        return outVector;
    }

    public static Vector2 angleToVectorRad (Vector2 outVector, float angle) {
        outVector.x = -com.badlogic.gdx.math.MathUtils.sin(angle);
        outVector.y = com.badlogic.gdx.math.MathUtils.cos(angle);
        return outVector;
    }
}
