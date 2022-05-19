package com.xam.bobgame.ai;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.ai.GdxAI;
import com.badlogic.gdx.ai.btree.utils.BehaviorTreeParser;
import com.badlogic.gdx.ai.btree.utils.DistributionAdapters;
import com.badlogic.gdx.ai.steer.SteeringAcceleration;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.PrefixFileHandleResolver;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.ObjectMap;
import com.xam.bobgame.ai.steering.AIArrive;
import com.xam.bobgame.ai.trees.GameBehaviorTreeLibrary;
import com.xam.bobgame.ai.trees.GameStateAdapters;
import com.xam.bobgame.ai.trees.TaskLibrary;
import com.xam.bobgame.components.AIComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.components.SteerableComponent;
import com.xam.bobgame.entity.ComponentMappers;

public class AISystem extends EntitySystem {

    public static final String BEHAVIOR_TREES_DIR = "trees";

    private ImmutableArray<Entity> aiEntities;
    private ObjectMap<Family, EntityListener> entityListeners = new ObjectMap<>();

    private DistributionAdapters distributionAdapters = new DistributionAdapters();
    private GameStateAdapters gameStateAdapters = new GameStateAdapters();
    private TaskLibrary aiTaskLibrary = new TaskLibrary(distributionAdapters, gameStateAdapters);
    private GameBehaviorTreeLibrary library = new GameBehaviorTreeLibrary(new PrefixFileHandleResolver(new InternalFileHandleResolver(), BEHAVIOR_TREES_DIR + "/"), aiTaskLibrary, BehaviorTreeParser.DEBUG_NONE);

    public AISystem(int priority) {
        super(priority);

        entityListeners.put(Family.all(SteerableComponent.class, PhysicsBodyComponent.class).get(), new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {
                SteerableComponent steerable = ComponentMappers.steerables.get(entity);
                PhysicsBodyComponent physicsBody = ComponentMappers.physicsBody.get(entity);
                AIComponent ai = ComponentMappers.ai.get(entity);

                steerable.physicsBody = physicsBody;
                if (ai != null) {
                    AIArrive<Vector2> steering = new AIArrive<>(steerable);
                    steering.setEnabled(true);
                    steering.set(ai, steerable);
                    ai.steering = steering;
                    ai.tree = library.createBehaviorTree("default", entity);
                }
            }

            @Override
            public void entityRemoved(Entity entity) {

            }
        });
    }

    @Override
    public void addedToEngine(Engine engine) {
        aiEntities = engine.getEntitiesFor(Family.all(AIComponent.class).get());
        for (ObjectMap.Entry<Family, EntityListener> entry : entityListeners) {
            engine.addEntityListener(entry.key, entry.value);
        }
    }

    @Override
    public void removedFromEngine(Engine engine) {
        aiEntities = null;
        for (ObjectMap.Entry<Family, EntityListener> entry : entityListeners) {
            engine.removeEntityListener(entry.value);
        }
    }

    private final SteeringAcceleration<Vector2> steeringAcceleration = new SteeringAcceleration<>(new Vector2());

    @Override
    public void update(float deltaTime) {
        GdxAI.getTimepiece().update(deltaTime);
        for (Entity entity : aiEntities) {
            AIComponent ai = ComponentMappers.ai.get(entity);
            PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
            ai.tree.step();
            ai.steering.calculateSteering(steeringAcceleration);
            Vector2 vel = pb.body.getLinearVelocity();
            pb.body.setLinearVelocity(vel.mulAdd(steeringAcceleration.linear, deltaTime));
        }
    }
}
