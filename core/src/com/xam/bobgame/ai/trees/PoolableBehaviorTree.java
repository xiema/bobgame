package com.xam.bobgame.ai.trees;

import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.Task;

public class PoolableBehaviorTree<E> extends BehaviorTree<E> {

    public PoolableBehaviorTree() {
        super();
    }

    public PoolableBehaviorTree(Task<E> rootTask) {
        super(rootTask);
    }

    public PoolableBehaviorTree(Task<E> rootTask, E object) {
        super(rootTask, object);
    }

    // Fixes the pooling in superclass which doesn't work properly
    @Override
    public void reset() {
        super.reset();
//        removeListeners();
//        setObject(null);
//        status = Status.FRESH;
//        getChild(0).resetTask();
//        this.tree = this;
        resetTask();
    }
}
