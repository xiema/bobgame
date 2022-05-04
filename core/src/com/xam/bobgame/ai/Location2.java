package com.xam.bobgame.ai;

import com.badlogic.gdx.ai.utils.Location;
import com.badlogic.gdx.math.Vector2;
import com.xam.bobgame.utils.MathUtils2;

public class Location2 implements Location<Vector2> {

    protected Vector2 vector = new Vector2(0, 0);
    protected float orientation = 0;

    public void setPosition (float x, float y) {
        vector.set(x, y);
    }

    @Override
    public Vector2 getPosition() {
        return vector;
    }

    public float getX() { return vector.x; }
    public float getY() { return vector.y; }

    @Override
    public float getOrientation() {
        return orientation * com.badlogic.gdx.math.MathUtils.degreesToRadians;
    }

    public float getOrientationDeg() {
        return orientation;
    }

    @Override
    public void setOrientation(float orientation) {
        this.orientation = orientation * com.badlogic.gdx.math.MathUtils.radiansToDegrees;
    }

    public void setOrientationDeg(float orientation) {
        this.orientation = orientation;
    }

    @Override
    public float vectorToAngle(Vector2 vector) {
        return MathUtils2.vectorToAngleRad(vector);
    }

    @Override
    public Vector2 angleToVector(Vector2 outVector, float angle) {
        return MathUtils2.angleToVectorRad(outVector, angle);
    }

    @Override
    public Location2 newLocation() {
        return new Location2();
    }

    @Override
    public String toString() {
        return vector.toString() + " " + orientation;
    }
}
