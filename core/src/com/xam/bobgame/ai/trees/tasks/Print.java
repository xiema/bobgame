package com.xam.bobgame.ai.trees.tasks;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.annotation.TaskAttribute;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.entity.ComponentMappers;

/**
 * Debugging task for printing a message on system out.
 */
public class Print<E> extends LeafTask<E> implements LibraryTask<E> {
    @TaskAttribute(required = true)
    public String message;

    @Override
    public Status execute() {
        E object = getObject();
        if (object instanceof Entity) {
            int entityId = ComponentMappers.identity.get((Entity) object).id;
            Log.info("AI:" + entityId, message);
        }
        else {
            Log.info(object.toString() + ":", message);
        }
        return Status.SUCCEEDED;
    }

    @Override
    protected Task<E> copyTo(Task<E> task) {
        Print<E> otherTask = (Print<E>) task;
        otherTask.message = message;
        return otherTask;
    }

    @Override
    public Task<E> copyToTask(Task<E> task) {
        return copyTo(task);
    }
}
