package com.xam.bobgame.ai.trees.tasks;

import com.badlogic.gdx.ai.GdxAI;
import com.badlogic.gdx.ai.btree.Decorator;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.annotation.TaskAttribute;

public class WaitSuccess<E> extends Decorator<E> implements LibraryTask<E> {
    @TaskAttribute
    public float interval = 0;

    protected float startTime;
    protected boolean initialized = false;

    public WaitSuccess () {
    }

    public WaitSuccess (Task<E> task) {
        super(task);
    }

    @Override
    public void run() {
        float currentTime = GdxAI.getTimepiece().getTime();
        if (getStatus() != Status.RUNNING || currentTime - startTime >= interval) {
            super.run();
            startTime = currentTime;
        }
    }

    @Override
    public void childFail (Task<E> runningTask) {
        running();
    }

    @Override
    protected Task<E> copyTo(Task<E> task) {
        WaitSuccess<E> otherTask = (WaitSuccess<E>) task;
        otherTask.interval = interval;
        return super.copyTo(otherTask);
    }

    @Override
    public void reset() {
        super.reset();
        interval = 0;
        initialized = false;
    }

    @Override
    public Task<E> copyToTask(Task<E> task) {
        return copyTo(task);
    }
}
