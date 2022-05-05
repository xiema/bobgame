package com.xam.bobgame.ai.steering;

import com.badlogic.gdx.ai.steer.Limiter;
import com.badlogic.gdx.ai.steer.Steerable;
import com.badlogic.gdx.ai.steer.SteeringAcceleration;
import com.badlogic.gdx.ai.steer.behaviors.Arrive;
import com.badlogic.gdx.ai.utils.Location;
import com.badlogic.gdx.math.Vector;
import com.badlogic.gdx.math.Vector2;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.components.AIComponent;

public class AIArrive<T extends Vector<T>> extends Arrive<T> {

    public AIArrive(Steerable<T> owner) {
        super(owner);
    }

    private AIComponent ai;

    protected T offset;

    private Location<T> tempLoc;

    @SuppressWarnings("unchecked")
    public void set(AIComponent ai, Steerable<T> steerable) {
        setOwner(steerable);
        setTarget((Location<T>) ai.target);
        setLimiter(steerable);
        setArrivalTolerance(ai.minDistance);
        this.ai = ai;
        if (offset == null) {
            offset = (T) steerable.newLocation().getPosition();
        }
        tempLoc = owner.newLocation();
    }

    public void setOffset(T offset) {
        this.offset.set(offset);
    }

    public T getOffset() {
        return offset;
    }

    @Override
    protected SteeringAcceleration<T> arrive (SteeringAcceleration<T> steering, T targetPosition) {
        // Get the direction and distance to the target
        T toTarget = steering.linear.set(targetPosition).sub(owner.getPosition()).add(offset);
        float distance = toTarget.len();

        // Check if we are there, return no steering
        if (distance <= arrivalTolerance) return steering.setZero();

        Limiter actualLimiter = getActualLimiter();
        // Go max speed
        float targetSpeed = actualLimiter.getMaxLinearSpeed();

        // If we are inside the slow down radius calculate a scaled speed
        if (distance < decelerationRadius) targetSpeed *= distance / decelerationRadius;

        // Target velocity combines speed and direction
        T targetVelocity = toTarget.scl(targetSpeed / distance); // Optimized code for: toTarget.nor().scl(targetSpeed)

        // Acceleration tries to get to the target velocity without exceeding max acceleration
        // Notice that steering.linear and targetVelocity are the same vector
        targetVelocity.sub(owner.getLinearVelocity()).scl(1f / timeToTarget).limit(actualLimiter.getMaxLinearAcceleration());

        // No angular acceleration
        steering.angular = 0f;

        // Output the steering
        return steering;
    }

    float maxPredictionTime = 1f;

    public void setMaxPredictionTime(float maxPredictionTime) {
        this.maxPredictionTime = maxPredictionTime;
    }

//    protected SteeringAcceleration<T> pursue (SteeringAcceleration<T> steering) {
//        T targetPosition = target.getPosition();
//
//        // Get the square distance to the evader (the target)
//        float squareDistance = steering.linear.set(targetPosition).add(offset).sub(owner.getPosition()).len2();
//
//        // Work out our current square speed
//        float squareSpeed = owner.getLinearVelocity().len2();
//
//        float predictionTime = maxPredictionTime;
//
//        if (squareSpeed > 0) {
//            // Calculate prediction time if speed is not too small to give a reasonable value
//            float squarePredictionTime = squareDistance / squareSpeed;
//            if (squarePredictionTime < maxPredictionTime * maxPredictionTime)
//                predictionTime = (float)Math.sqrt(squarePredictionTime);
//        }
//
//        // Calculate and seek/flee the predicted position of the target
//        steering.linear.set(targetPosition).mulAdd(worldMovementVector, -predictionTime).sub(owner.getPosition()).nor()
//                .scl(getActualLimiter().getMaxLinearAcceleration());
//
//        // No angular acceleration
//        steering.angular = 0;
//
//        // Output steering acceleration
//        return steering;
//    }

    protected SteeringAcceleration<T> pursue(SteeringAcceleration<T> steering, T targetPosition) {
        float maxAccel = getLimiter().getMaxLinearAcceleration();
        float maxVel= getLimiter().getMaxLinearSpeed();
        T disp = steering.linear.set(targetPosition).sub(getOwner().getPosition());

        T vel = getOwner().getLinearVelocity();
        float dot = disp.dot(vel);
        if (dot <= 0) {
            return steer(steering, targetPosition);
        }

        float dist = disp.len();
        float spd = maxVel;

        float sinj = (float) Math.sin(dot / (dist * spd));
        float r = dist / (2 * sinj);
        float reqAccel = Math.min(maxAccel, Math.abs((r * spd / (2 - r))));

        T dir = tempLoc.getPosition();
        dir.set(vel).scl(-dot / vel.dot(vel)).add(disp).nor();
        if (disp.dot(dir) * disp.dot(vel) < 0) {
            dir.scl(-reqAccel);
        }
        else {
            dir.scl(reqAccel);
        }

        float q = (float) Math.sqrt(Math.abs(spd / 2 - r));

        float remAccel = reqAccel == maxAccel ? 0 : (float) Math.sqrt(maxAccel * maxAccel - reqAccel * reqAccel);
        steering.linear.set(vel).nor().scl(spd).sub(vel).scl(1 / GameProperties.SIMULATION_UPDATE_INTERVAL).limit(remAccel).add(dir);

        return steering;
    }

    protected SteeringAcceleration<T> steer(SteeringAcceleration<T> steering, T targetPosition) {
        T vel = getOwner().getLinearVelocity();
        T dir = steering.linear.set(targetPosition).sub(owner.getPosition());
        float dist = dir.len();
        dir.scl(1 / dist);

        float maxAccel = getLimiter().getMaxLinearAcceleration();
        float projMagnitude = vel.dot(dir);

        T rej = tempLoc.getPosition();
        rej.set(dir).scl(-projMagnitude).add(vel);
        float rejMagnitude = rej.len();

        float t1 = rejMagnitude / maxAccel;
        if (t1 >= GameProperties.SIMULATION_UPDATE_INTERVAL) {
            dir.set(rej).nor().scl(-maxAccel);
            return steering;
        }

        rej.scl(1 / GameProperties.SIMULATION_UPDATE_INTERVAL);

        maxAccel -= rejMagnitude / GameProperties.SIMULATION_UPDATE_INTERVAL;

        if (projMagnitude > 0) {
            float d = dist - projMagnitude * GameProperties.SIMULATION_UPDATE_INTERVAL - 0.5f * maxAccel * GameProperties.SIMULATION_UPDATE_INTERVAL * GameProperties.SIMULATION_UPDATE_INTERVAL;
            float v = projMagnitude + maxAccel * GameProperties.SIMULATION_UPDATE_INTERVAL;
            if (v * v / (d * 2) < maxAccel) {
                dir.scl(maxAccel);
            }
            else {
                float s = (projMagnitude * projMagnitude) / (dist * 2);
                dir.scl(-s);
            }
        }
        else {
            if (-projMagnitude * GameProperties.SIMULATION_UPDATE_INTERVAL + 0.5f * maxAccel * (GameProperties.SIMULATION_UPDATE_INTERVAL * GameProperties.SIMULATION_UPDATE_INTERVAL) > dist) {
                float s = -projMagnitude / GameProperties.SIMULATION_UPDATE_INTERVAL;
                dir.scl(s);
            }
            else {
                dir.scl(maxAccel);
            }
        }

        dir.sub(rej);

        return steering;
    }

    @Override
    protected SteeringAcceleration<T> calculateRealSteering(SteeringAcceleration<T> steering) {
        T targetPosition = getTarget().getPosition();
//        if (ai.relativeSteering) return pursue(steering);
//        if (targetPosition.dst(getOwner().getPosition()) < getArrivalTolerance()) {
//            steering.linear.set(getOwner().getLinearVelocity().scl(-1.0f));
//        }
//        else {
//            arrive(steering, targetPosition);
//            if (steering.linear.len2() < getLimiter().getZeroLinearSpeedThreshold()) steering.linear.setZero();
//        }
//        steer(steering, targetPosition);
        pursue(steering, targetPosition);
        return steering;
    }
}
