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

public class ArriveTask extends LeafTask<Entity> implements LibraryTask<Entity> {
    @TaskAttribute
    public GameStates.FloatGameState<Entity> minDistance = null;

    private Vector2 tempVec = new Vector2();

    @Override
    public Status execute() {
        Entity entity = getObject();
        AIComponent ai = ComponentMappers.ai.get(entity);

        float d = minDistance == null ? ai.minDistance : minDistance.getFloat();

        PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);

        if (tempVec.set(ai.target.getPosition()).sub(pb.body.getPosition()).len2() <= d * d) {
            return Status.SUCCEEDED;
        }

        return Status.RUNNING;
    }

    @Override
    protected Task<Entity> copyTo(Task<Entity> task) {
        ArriveTask other = (ArriveTask) task;
        other.minDistance = minDistance == null ? null : minDistance.clone(other);
        return other;
    }

    @Override
    public void reset() {
        super.reset();
        if (minDistance != null) Pools.free(minDistance);
        minDistance = null;
    }

    @Override
    public Task<Entity> copyToTask(Task<Entity> task) {
        return copyTo(task);
    }
}
