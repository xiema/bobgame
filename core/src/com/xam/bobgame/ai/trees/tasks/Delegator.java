package com.xam.bobgame.ai.trees.tasks;

import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.annotation.TaskConstraint;

@TaskConstraint(minChildren = 0, maxChildren = 1)
public abstract class Delegator<E> extends LeafTask<E> implements LibraryTask<E> {

    private static int nextId = 0;

    private Task<E> delegate;
    private String delegateId;

    public Delegator() {
    }

    @Override
    protected int addChildToTask(Task<E> child) {
        setDelegate(child);
        return 0;
    }

    public void setDelegate(Task<E> task) {
        if (delegate != null) throw new IllegalStateException("Delegator can have only one child task");
        delegate = task;
        delegateId = "Delegate." + nextId++;
    }

    @Override
    public int getChildCount() {
        return delegate == null ? 0 : 1;
    }

    public Task<E> getDelegate() {
        return delegate;
    }

    public String getDelegateId() {
        return delegateId;
    }

    @Override
    public Task<E> getChild(int i) {
        if (i == 0 && delegate != null) return delegate;
        throw new IndexOutOfBoundsException("index can't be >= size: " + i + " >= " + getChildCount());
    }

    @Override
    protected Task<E> copyTo(Task<E> task) {
        Delegator<E> other = (Delegator<E>) task;
        other.delegate = delegate.cloneTask();
        other.delegateId = delegateId;
        return task;
    }

    @Override
    public Task<E> copyToTask(Task<E> task) {
        return copyTo(task);
    }

    @Override
    public void reset() {
        super.reset();
//        if (TASK_CLONER != null) TASK_CLONER.freeTask(delegate);
        delegate = null;
        delegateId = null;
    }
}
