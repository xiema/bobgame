package com.xam.bobgame.ai.trees;

import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.utils.Null;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;

public abstract class GameState<E> implements Pool.Poolable {
    protected Task<E> task;

    public GameState() {

    }

    public void setTask(Task<E> task) {
        this.task = task;
    }

    public GameState<E> clone(Task<E> toTask) {
//        try {
//            @SuppressWarnings("unchecked")
//            GameState<E> clone = copyTo(ClassReflection.newInstance(this.getClass()));
//            clone.task = toTask;
//            return clone;
//        } catch (ReflectionException e) {
//            throw new GdxRuntimeException(e);
//        }
        //noinspection unchecked
        GameState<E> clone = Pools.obtain(this.getClass());
        copyTo(clone);
        clone.task = toTask;
        return clone;
    }

    public abstract GameState<E> copyTo(GameState<E> gameState);

    @Override
    public void reset() {
        task = null;
    }
}
