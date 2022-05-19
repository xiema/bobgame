package com.xam.bobgame.ai.steering;

import com.badlogic.gdx.ai.steer.Steerable;
import com.badlogic.gdx.ai.steer.SteeringAcceleration;
import com.badlogic.gdx.ai.steer.SteeringBehavior;
import com.badlogic.gdx.ai.utils.Location;
import com.badlogic.gdx.math.Vector;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.components.AIComponent;

public class AIArrive<T extends Vector<T>> extends SteeringBehavior<T> {

    public AIArrive(Steerable<T> owner) {
        super(owner);
    }

    private AIComponent ai;
    private Location<T> target;

    protected T offset;

    private Location<T> tempLoc;

    @SuppressWarnings("unchecked")
    public void set(AIComponent ai, Steerable<T> steerable) {
        this.ai = ai;
        target = (Location<T>) ai.target;
        setOwner(steerable);
        setLimiter(steerable);
        if (offset == null) {
            offset = steerable.newLocation().getPosition();
        }
        tempLoc = owner.newLocation();
    }

    public void setOffset(T offset) {
        this.offset.set(offset);
    }

    public T getOffset() {
        return offset;
    }

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
        pursue(steering, target.getPosition());
        return steering;
    }
}
