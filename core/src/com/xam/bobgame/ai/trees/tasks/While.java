package com.xam.bobgame.ai.trees.tasks;

import com.badlogic.gdx.ai.btree.Decorator;
import com.badlogic.gdx.ai.btree.Task;

public abstract class While<E> extends Decorator<E> implements LibraryTask<E> {
    public While () {
    }

    public While (Task<E> task) {
        super(task);
    }

    protected abstract boolean condition();

    @Override
    public void run() {
        if (condition()) {
            super.run();
        }
        else {
            success();
        }
    }

    @Override
    public void childFail (Task<E> runningTask) {
        running();
    }

    @Override
    public void childSuccess(Task<E> runningTask) {
        running();
    }

    @Override
    public Task<E> copyToTask(Task<E> task) {
        return copyTo(task);
    }
}
