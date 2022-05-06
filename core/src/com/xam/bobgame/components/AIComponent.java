package com.xam.bobgame.components;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.steer.SteeringBehavior;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.xam.bobgame.ai.AIMemory;
import com.xam.bobgame.ai.Location2;

public class AIComponent extends Component2 implements Poolable {

    public Location2 target = new Location2();
    public BehaviorTree<Entity> tree;
    public AIMemory memory = new AIMemory();
    public SteeringBehavior<Vector2> steering;
    public float minDistance = 1f;

    @Override
    public void reset() {
        tree = null;
        memory.clear();
        steering = null;
    }
}