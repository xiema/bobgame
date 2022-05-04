package com.xam.bobgame.ai.trees.tasks;

import com.badlogic.gdx.ai.btree.Decorator;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.annotation.TaskAttribute;

/**
 * Similar to {@link com.badlogic.gdx.ai.btree.decorator.Repeat}, but has a state that's persistent over time.
 */
public class Loop<E> extends Decorator<E> implements LibraryTask<E> {
    @TaskAttribute
    public int times;
    private int count = 0;


    public Loop() {
        this(null);
    }

    public Loop(Task<E> child) {
        this(0, child);
    }

    public Loop(int times, Task<E> child) {
        super(child);
        this.times = times;
    }

    @Override
    public void start() {
        count = 0;
    }

    @Override
    public void run () {
        if (child.getStatus() == Status.RUNNING) {
            child.run();
        } else {
            child.setControl(this);
            child.start();
            if (child.checkGuard(this))
                child.run();
            else
                child.fail();
        }
    }

    @Override
    public void childSuccess (Task<E> runningTask) {
        count++;
        if (count == times) success();
        running();
    }

    @Override
    public void childFail (Task<E> runningTask) {
        count++;
        if (count == times) success();
        running();
    }

    @Override
    protected Task<E> copyTo (Task<E> task) {
        Loop<E> otherTask = (Loop<E>) task;
        otherTask.times = times;

        return super.copyTo(task);
    }

    @Override
    public Task<E> copyToTask(Task<E> task) {
        return copyTo(task);
    }

    @Override
    public void reset() {
        super.reset();
        count = 0;
        times = 0;
    }
}