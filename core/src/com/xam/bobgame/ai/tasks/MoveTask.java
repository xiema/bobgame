package com.xam.bobgame.ai.tasks;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.annotation.TaskAttribute;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pools;
import com.xam.bobgame.ai.trees.GameStates;
import com.xam.bobgame.ai.trees.tasks.LibraryTask;
import com.xam.bobgame.components.AIComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;

public class MoveTask extends LeafTask<Entity> implements LibraryTask<Entity> {
    @TaskAttribute
    public GameStates.FloatGameState<Entity> x;
    @TaskAttribute
    public GameStates.FloatGameState<Entity> y;
    @TaskAttribute
    public GameStates.FloatGameState<Entity> speed;

    @Override
    public Status execute() {
        Entity entity = getObject();
        AIComponent ai = ComponentMappers.ai.get(entity);

//        PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
//        Vector2 position = pb.body.getPosition();
//
//        float realX = x == null ? position.x : x.getFloat();
//        float realY = y == null ? position.y : y.getFloat();
//        float realSpeed = speed.getFloat();
//
//        ai.target.setPosition(realX, realY);

        return Status.RUNNING;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        MoveTask other = (MoveTask) task;
        other.x = x == null ? null : x.clone(other);
        other.y = y == null ? null : y.clone(other);
        other.speed = speed == null ? null : speed.clone(other);
        return other;
    }

    @Override
    public void reset() {
        super.reset();
        if (x != null) Pools.free(x);
        x = null;
        if (y != null) Pools.free(y);
        y = null;
        if (speed != null) Pools.free(speed);
        speed = null;
    }

    @Override
    public Task<Entity> copyToTask(Task<Entity> task) {
        return copyTo(task);
    }
}
