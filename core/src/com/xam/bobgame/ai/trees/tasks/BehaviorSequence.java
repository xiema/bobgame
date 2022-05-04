package com.xam.bobgame.ai.trees.tasks;

import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.Task;

public class BehaviorSequence<E> extends BehaviorTree<E> implements LibraryTask<E> {

    private StepSequence<E> rootTask;

    public BehaviorSequence() {
        super();
    }

    public BehaviorSequence(Task<E> rootTask) {
        super(rootTask);
        if (!(rootTask instanceof StepSequence)) throw new IllegalStateException("BehaviorSequence must have a StepSequence as a child");
        this.rootTask = (StepSequence<E>) rootTask;
    }

    public BehaviorSequence(Task<E> rootTask, E object) {
        super(rootTask, object);
        if (!(rootTask instanceof StepSequence)) throw new IllegalStateException("BehaviorSequence must have a StepSequence as a child");
        this.rootTask = (StepSequence<E>) rootTask;
    }

    @Override
    protected int addChildToTask(Task<E> child) {
        if (getChildCount() == 0 && !(child instanceof StepSequence)) throw new IllegalStateException("BehaviorSequence must have a StepSequence as a child");
        this.rootTask = (StepSequence<E>) child;
        return super.addChildToTask(child);
    }

    public boolean isFinished() {
        return rootTask == null || rootTask.isFinished();
    }

    @Override
    protected Task<E> copyTo(Task<E> task) {
        super.copyTo(task);
        ((BehaviorSequence<E>) task).rootTask = (StepSequence<E>) task.getChild(0);
        return task;
    }

    @Override
    public void reset() {
        super.reset();
        rootTask = null;
    }

    @Override
    public Task<E> copyToTask(Task<E> task) {
        return copyTo(task);
    }
}
