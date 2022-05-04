package com.xam.bobgame.ai.trees.tasks;

import com.badlogic.gdx.ai.btree.LeafTask;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.annotation.TaskAttribute;
import com.badlogic.gdx.utils.Pools;
import com.xam.bobgame.ai.trees.GameStates;

public class CheckFloat<E> extends LeafTask<E> implements LibraryTask<E> {
    @TaskAttribute
    public GameStates.FloatGameState<E> f1;
    @TaskAttribute
    public GameStates.FloatGameState<E> f2;
    @TaskAttribute
    public CheckType type;

    @Override
    public Status execute() {
        boolean b = false;
        switch (type) {
            case LessThan:
                b = f1.getFloat() < f2.getFloat();
                break;
            case GreaterThan:
                b = f1.getFloat() > f2.getFloat();
                break;
            case LessThanOrEqual:
                b = f1.getFloat() <= f2.getFloat();
                break;
            case GreaterThanOrEqual:
                b = f1.getFloat() >= f2.getFloat();
                break;
            case Equal:
                b = f1.getFloat() == f2.getFloat();
                break;
            case NotEqual:
                b = f1.getFloat() != f2.getFloat();
                break;
        }
        return b ? Status.SUCCEEDED : Status.FAILED;
    }

    @Override
    protected Task<E> copyTo(Task<E> task) {
        CheckFloat<E> other = (CheckFloat<E>) task;
        other.f1 = f1 == null ? null : f1.clone(other);
        other.f2 = f2 == null ? null : f2.clone(other);
        other.type = type;
        return other;
    }

    @Override
    public Task<E> copyToTask(Task<E> task) {
        return copyTo(task);
    }

    @Override
    public void reset() {
        super.reset();
        if (f1 != null) Pools.free(f1);
        f1 = null;
        if (f2 != null) Pools.free(f2);
        f2 = null;
        type = null;
    }

    public enum CheckType {
        LessThan, GreaterThan, Equal, NotEqual, LessThanOrEqual, GreaterThanOrEqual,
    }
}
