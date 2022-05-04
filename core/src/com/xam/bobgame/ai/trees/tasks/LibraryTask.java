package com.xam.bobgame.ai.trees.tasks;

import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.utils.Pool;

public interface LibraryTask<E> extends Pool.Poolable {

    Task<E> cloneTask();

    Task<E> copyToTask(Task<E> task);
}
