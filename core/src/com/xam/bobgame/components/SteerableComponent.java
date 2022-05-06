package com.xam.bobgame.components;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.ai.steer.Steerable;
import com.badlogic.gdx.ai.utils.Location;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Transform;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.badlogic.gdx.utils.Pools;
import com.xam.bobgame.ai.Location2;
import com.xam.bobgame.utils.BitPacker;
import com.xam.bobgame.utils.MathUtils2;

public class SteerableComponent implements Component2, Steerable<Vector2>, Poolable {

    public PhysicsBodyComponent physicsBody;
    public float maxLinearSpeed = 0.0f;
    public float maxLinearAcceleration = 0.0f;
    public float maxAngularSpeed = 0.0f;
    public float maxAngularAcceleration = 0.0f;

    public float boundingRadius = 0.0f;

    @Override
    public void reset() {
        physicsBody = null;
    }

    // STEERABLE METHODS
    // Below are methods that Steerable needs to implement in order to work properly.

    // variables needed by the methods
    public boolean tagged = false;
    public float zeroLinearSpeedThreshold = 0.02f;

    private Vector2 tempVec = new Vector2();

    @Override
    public Vector2 getLinearVelocity() {
        return physicsBody.body.getLinearVelocity();
    }

    @Override
    public float getAngularVelocity() {
        return physicsBody.body.getAngularVelocity();
    }

    @Override
    public float getBoundingRadius() {
        return boundingRadius;
    }

    @Override
    public boolean isTagged() {
        return tagged;
    }

    @Override
    public void setTagged(boolean tagged) {
        this.tagged = tagged;
    }

    @Override
    public float getZeroLinearSpeedThreshold() {
        return zeroLinearSpeedThreshold;
    }

    @Override
    public void setZeroLinearSpeedThreshold(float value) {
        zeroLinearSpeedThreshold = value;
    }

    @Override
    public float getMaxLinearSpeed() {
        return maxLinearSpeed;
    }

    @Override
    public void setMaxLinearSpeed(float maxLinearSpeed) {
        this.maxLinearSpeed = maxLinearSpeed;
    }

    @Override
    public float getMaxLinearAcceleration() {
        return maxLinearAcceleration;
    }

    @Override
    public void setMaxLinearAcceleration(float maxLinearAcceleration) {
        this.maxLinearAcceleration = maxLinearAcceleration;
    }

    @Override
    public float getMaxAngularSpeed() {
        return maxAngularSpeed;
    }

    @Override
    public void setMaxAngularSpeed(float maxAngularSpeed) {
        this.maxAngularSpeed = maxAngularSpeed;
    }

    @Override
    public float getMaxAngularAcceleration() {
        return maxAngularAcceleration;
    }

    @Override
    public void setMaxAngularAcceleration(float maxAngularAcceleration) {
        this.maxAngularAcceleration = maxAngularAcceleration;
    }

    @Override
    public Vector2 getPosition() {
        return physicsBody.body.getPosition();
    }

    @Override
    public float getOrientation() {
        return physicsBody.body.getAngle();
    }

    @Override
    public void setOrientation(float orientation) {
        Transform tfm = physicsBody.body.getTransform();
        physicsBody.body.setTransform(tfm.vals[0], tfm.vals[1], orientation);
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
    public Location<Vector2> newLocation() {
        return Pools.obtain(Location2.class);
    }

    @Override
    public int read(BitPacker packer, Engine engine) {
        return 0;
    }
}
