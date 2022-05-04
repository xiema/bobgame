package com.xam.bobgame.ai.trees.tasks;

import com.badlogic.gdx.ai.btree.SingleRunningChildBranch;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.annotation.TaskAttribute;
import com.badlogic.gdx.utils.Array;

public class StepSequence<E> extends SingleRunningChildBranch<E> implements LibraryTask<E> {

    @TaskAttribute
    public boolean waitSuccess = false;

    private boolean finished = false;

    public StepSequence() {
        super();
    }

    public StepSequence(Task<E>... tasks) {
        super(new Array<>(tasks));
    }

    public StepSequence(Array<Task<E>> tasks) {
        super(tasks);
    }

    @Override
    public void run() {
        if (!finished) super.run();
    }

    @Override
    public void childSuccess(Task<E> runningTask) {
        runningChild = null;
        if (++currentChildIndex < children.size) {
            running(); // Run next child on next tick
        } else {
            success(); // All children processed, return success status
            finished = true;
        }
    }

    @Override
    public void childFail(Task<E> runningTask) {
        runningChild = null;
        if (waitSuccess) {
            running(); // Rerun on next tick
        }
        else if (++currentChildIndex < children.size) {
            running(); // Run next child on next tick
        } else {
            success(); // All children processed, return success status
            finished = true;
        }
    }

    @Override
    protected Task<E> copyTo(Task<E> task) {
        ((StepSequence<E>) task).waitSuccess = waitSuccess;
        return super.copyTo(task);
    }

    public boolean isFinished() {
        return finished;
    }

    @Override
    public void resetTask() {
        super.resetTask();
        finished = false;
    }

    @Override
    public void reset() {
        super.reset();
        waitSuccess = false;
        finished = false;
    }

    @Override
    public Task<E> copyToTask(Task<E> task) {
        return copyTo(task);
    }
}
