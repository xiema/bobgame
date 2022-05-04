package com.xam.bobgame.ai.trees.tasks;

import com.badlogic.gdx.ai.btree.Decorator;
import com.badlogic.gdx.ai.btree.Task;

public class WaitWhile<E> extends Decorator<E> implements LibraryTask<E> {

    @Override
    public void childRunning(Task<E> runningTask, Task<E> reporter) {
        running();
    }

    @Override
    public void childFail(Task<E> runningTask) {
        success();
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
