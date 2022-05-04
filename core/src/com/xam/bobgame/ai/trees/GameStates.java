package com.xam.bobgame.ai.trees;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.utils.random.Distribution;
import com.badlogic.gdx.ai.utils.random.FloatDistribution;
import com.badlogic.gdx.ai.utils.random.IntegerDistribution;
import com.badlogic.gdx.math.Vector2;
import com.xam.bobgame.GameDirector;
import com.xam.bobgame.components.AIComponent;
import com.xam.bobgame.components.Component2;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.utils.MathUtils2;

/**
 * Dynamic BehaviorTree Task Attributes.
 */
public class GameStates {

    static public abstract class FloatGameState<E> extends com.xam.bobgame.ai.trees.GameState<E> {

        public FloatGameState() {
            super();
        }

        @Override
        public FloatGameState<E> clone(Task<E> toTask) {
            return (FloatGameState<E>) super.clone(toTask);
        }

        public abstract float getFloat();
    }

    static public abstract class IntGameState<E> extends com.xam.bobgame.ai.trees.GameState<E> {

        public IntGameState() {
            super();
        }

        @Override
        public IntGameState<E> clone(Task<E> toTask) {
            return (IntGameState<E>) super.clone(toTask);
        }

        public abstract int getInt();
    }

    static public abstract class StringGameState<E> extends com.xam.bobgame.ai.trees.GameState<E> {

        public StringGameState() {
            super();
        }

        @Override
        public StringGameState<E> clone(Task<E> toTask) {
            return (StringGameState<E>) super.clone(toTask);
        }

        public abstract String getString();
    }

    static public abstract class EntityGameState<E> extends com.xam.bobgame.ai.trees.GameState<E> {

        public EntityGameState() {
            super();
        }

        @Override
        public EntityGameState<E> clone(Task<E> toTask) {
            return (EntityGameState<E>) super.clone(toTask);
        }

        public abstract Entity getEntity();
    }

    static public class ConstantFloatGameState<E> extends FloatGameState<E> {

        float value;

        public ConstantFloatGameState() {
            super();
        }

        @Override
        public com.xam.bobgame.ai.trees.GameState<E> copyTo(com.xam.bobgame.ai.trees.GameState<E> gameState) {
            ConstantFloatGameState<E> other = (ConstantFloatGameState<E>) gameState;
            other.value = value;
            return gameState;
        }

        public ConstantFloatGameState(float value) {
            this.value = value;
        }

        @Override
        public float getFloat() {
            return value;
        }

        public float getValue() {
            return value;
        }
    }

    static public class ConstantIntGameState<E> extends IntGameState<E> {

        int value;

        public ConstantIntGameState() {
            super();
        }

        @Override
        public com.xam.bobgame.ai.trees.GameState<E> copyTo(com.xam.bobgame.ai.trees.GameState<E> gameState) {
            ConstantIntGameState<E> other = (ConstantIntGameState<E>) gameState;
            other.value = value;
            return gameState;
        }

        public ConstantIntGameState(int value) {
            this.value = value;
        }

        @Override
        public int getInt() {
            return value;
        }

        public int getValue() {
            return value;
        }
    }

    static public final class SpecialEntityGameState<E> extends EntityGameState<E> {

        Engine engine;
        Type type;

        @Override
        public com.xam.bobgame.ai.trees.GameState<E> copyTo(com.xam.bobgame.ai.trees.GameState<E> gameState) {
            SpecialEntityGameState<E> other = (SpecialEntityGameState<E>) gameState;
            other.engine = engine;
            other.type = type;
            return other;
        }

        @Override
        public Entity getEntity() {
//            switch (type) {
//                case player:
//                    if (engine != null) {
//                        SpawnSystem spawnSystem = engine.getSystem(SpawnSystem.class);
//                        if (spawnSystem != null) {
//                            return spawnSystem.getPlayerEntity();
//                        }
//                    }
//            }
            return null;
        }

        @Override
        public void reset() {
            super.reset();
            type = null;
        }

        public enum Type {
            player,
        }
    }

    static public final class DefinitionFloatGameState<E> extends ConstantFloatGameState<E> {

        String[] originalParameters;

        public DefinitionFloatGameState() {
            super();
        }

        public DefinitionFloatGameState(float value, String[] originalParameters) {
            super(value);
            this.originalParameters = originalParameters;
        }

        @Override
        public com.xam.bobgame.ai.trees.GameState<E> copyTo(com.xam.bobgame.ai.trees.GameState<E> gameState) {
            DefinitionFloatGameState<E> other = (DefinitionFloatGameState<E>) gameState;
            other.originalParameters = originalParameters;
            return super.copyTo(gameState);
        }

        @Override
        public float getFloat() {
            return super.getFloat();
        }

        public String[] toParameters() {
            return originalParameters;
        }

        @Override
        public void reset() {
            super.reset();
            originalParameters = null;
        }
    }

    public static final class DistributionFloatGameState<E> extends FloatGameState<E> {

        FloatDistribution distribution;

        public DistributionFloatGameState() {
            super();
        }

        public DistributionFloatGameState(Distribution distribution) {
            this.distribution = (FloatDistribution) distribution;
        }

        @Override
        public com.xam.bobgame.ai.trees.GameState<E> copyTo(com.xam.bobgame.ai.trees.GameState<E> gameState) {
            DistributionFloatGameState<E> other = (DistributionFloatGameState<E>) gameState;
            other.distribution = distribution;
            return gameState;
        }

        @Override
        public float getFloat() {
            return distribution.nextFloat();
        }

        public FloatDistribution getDistribution() {
            return distribution;
        }

        @Override
        public void reset() {
            super.reset();
            distribution = null;
        }
    }

    public static final class DistributionIntGameState<E> extends IntGameState<E> {

        IntegerDistribution distribution;

        public DistributionIntGameState() {
            super();
        }

        public DistributionIntGameState(Distribution distribution) {
            this.distribution = (IntegerDistribution) distribution;
        }

        @Override
        public com.xam.bobgame.ai.trees.GameState<E> copyTo(com.xam.bobgame.ai.trees.GameState<E> gameState) {
            DistributionIntGameState<E> other = (DistributionIntGameState<E>) gameState;
            other.distribution = distribution;
            return gameState;
        }

        @Override
        public int getInt() {
            return distribution.nextInt();
        }

        public IntegerDistribution getDistribution() {
            return distribution;
        }

        @Override
        public void reset() {
            super.reset();
            distribution = null;
        }
    }

    public static class EntityPositionState<E> extends FloatGameState<E> {
        Engine engine;
        String axis;
        float offset;
        String whichEntity;
        float lastX = 0, lastY = 0;

        public EntityPositionState() {
            super();
        }

        public EntityPositionState(String whichEntity, String axis, float offset) {
            super();
            this.whichEntity = whichEntity;
            this.axis = axis;
            this.offset = offset;
        }

        @Override
        public com.xam.bobgame.ai.trees.GameState<E> copyTo(com.xam.bobgame.ai.trees.GameState<E> gameState) {
            EntityPositionState<E> other = (EntityPositionState<E>) gameState;
            other.engine = engine;
            other.axis = axis;
            other.offset = offset;
            other.whichEntity = whichEntity;
            return other;
        }

        protected Entity getEntity() {
            switch (whichEntity) {
                case "self":
                    return (Entity) task.getObject();
//                case "player":
//                    return engine.getSystem(SpawnSystem.class).getPlayerEntity();
            }
            return null;
        }

        @Override
        public float getFloat() {
            Entity entity = getEntity();
            if (entity != null) {
                PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(entity);
                Vector2 pos = pb.body.getPosition();
                lastX = pos.x;
                lastY = pos.y;
            }
            return (axis.equals("x")) ? lastX + offset: lastY + offset;
        }

        public String getAxis() {
            return axis;
        }

        public float getOffset() {
            return offset;
        }

        public String getWhichEntity() {
            return whichEntity;
        }

        @Override
        public void reset() {
            super.reset();
            engine = null;
            axis = null;
            whichEntity = null;
        }
    }

    public static class TargetPositionState extends FloatGameState<Entity> {

        String axis;
        float offset;

        public TargetPositionState() {
            super();
        }

        public TargetPositionState(String axis, float offset) {
            super();
            this.axis = axis;
            this.offset = offset;
        }

        @Override
        public com.xam.bobgame.ai.trees.GameState<Entity> copyTo(com.xam.bobgame.ai.trees.GameState<Entity> gameState) {
            TargetPositionState other = (TargetPositionState) gameState;
            other.axis = axis;
            other.offset = offset;
            return other;
        }

        @Override
        public float getFloat() {
            if (task == null) return 0;
            Entity entity = task.getObject();
            if (entity == null) return 0;
            AIComponent ai = ComponentMappers.ai.get(entity);
            if (ai == null) return 0;
            return axis.equals("x") ? ai.target.getX() + offset : ai.target.getY() + offset;
        }

        public String getAxis() {
            return axis;
        }

        public float getOffset() {
            return offset;
        }

        @Override
        public void reset() {
            super.reset();
            axis = null;
        }
    }

    public static class BearingToEntityPositionGameState<E> extends FloatGameState<E> {
        Engine engine;
        String whichEntity;
        float lastBearing = 0;

        public BearingToEntityPositionGameState() {
            super();
        }

        public BearingToEntityPositionGameState(String whichEntity) {
            super();
            this.whichEntity = whichEntity;
        }

        @Override
        public com.xam.bobgame.ai.trees.GameState<E> copyTo(com.xam.bobgame.ai.trees.GameState<E> gameState) {
            BearingToEntityPositionGameState<E> other = (BearingToEntityPositionGameState<E>) gameState;
            other.engine = engine;
            other.whichEntity = whichEntity;
            return other;
        }

        protected Entity getEntity() {
            switch (whichEntity) {
                case "self":
                    return (Entity) task.getObject();
//                case "player":
//                    return engine.getSystem(SpawnSystem.class).getPlayerEntity();
            }
            return null;
        }

        @Override
        public float getFloat() {
            E object = task.getObject();
            Entity targetEntity = getEntity();
            if (object instanceof Entity && targetEntity != null) {
                Entity entity = (Entity) object;
                Vector2 position = ComponentMappers.physicsBody.get(entity).body.getPosition();
                Vector2 targetPosition = ComponentMappers.physicsBody.get(targetEntity).body.getPosition();
                lastBearing = MathUtils2.vectorToAngle(targetPosition.x - position.x, targetPosition.y - position.y);
            }
            return lastBearing;
        }

        public String getWhichEntity() {
            return whichEntity;
        }

        @Override
        public void reset() {
            super.reset();
            engine = null;
            whichEntity = null;
            lastBearing = 0;
        }
    }

    public static class MemoryFloatGameState extends FloatGameState<Entity> {
        String name;
        float offset = 0;

        @Override
        public com.xam.bobgame.ai.trees.GameState<Entity> copyTo(com.xam.bobgame.ai.trees.GameState<Entity> gameState) {
            MemoryFloatGameState other = (MemoryFloatGameState) gameState;
            other.name = name;
            other.offset = offset;
            return other;
        }

        @Override
        public float getFloat() {
            AIComponent ai = ComponentMappers.ai.get(task.getObject());
            return ai.memory.getFloat(name, 0) + offset;
        }

        @Override
        public void reset() {
            super.reset();
            name = null;
            offset = 0;
        }
    }

    public static class MemoryIntGameState extends IntGameState<Entity> {
        String name;
        int offset = 0;

        @Override
        public com.xam.bobgame.ai.trees.GameState<Entity> copyTo(com.xam.bobgame.ai.trees.GameState<Entity> gameState) {
            MemoryIntGameState other = (MemoryIntGameState) gameState;
            other.name = name;
            other.offset = offset;
            return other;
        }

        @Override
        public int getInt() {
            AIComponent ai = ComponentMappers.ai.get(task.getObject());
            return ai.memory.getInt(name, 0) + offset;
        }

        @Override
        public void reset() {
            super.reset();
            name = null;
            offset = 0;
        }
    }

    public static class MemoryStringGameState extends StringGameState<Entity> {
        String name;

        @Override
        public com.xam.bobgame.ai.trees.GameState<Entity> copyTo(com.xam.bobgame.ai.trees.GameState<Entity> gameState) {
            MemoryStringGameState other = (MemoryStringGameState) gameState;
            other.name = name;
            return other;
        }

        @Override
        public String getString() {
            AIComponent ai = ComponentMappers.ai.get(task.getObject());
            return ai.memory.getString(name, "");
        }

        @Override
        public void reset() {
            super.reset();
            name = null;
        }
    }

    public static class PlayerPositionState<E> extends FloatGameState<E> {
        Engine engine;
        String axis;
        float offset;
        int playerId;

        static float[] lastPlayerX = new float[NetDriver.MAX_CLIENTS];
        static float[] lastPlayerY = new float[NetDriver.MAX_CLIENTS];

        public PlayerPositionState() {
            super();
        }

        public PlayerPositionState(Engine engine, String axis, float offset) {
            this.engine = engine;
            this.axis = axis;
            this.offset = offset;
        }

        @Override
        public float getFloat() {
            GameDirector gameDirector = engine.getSystem(GameDirector.class);
            Entity entity;
            if (gameDirector != null && (entity = gameDirector.getPlayerEntity(playerId)) != null) {
                Vector2 position = ComponentMappers.physicsBody.get(entity).body.getPosition();
                lastPlayerX[playerId] = position.x;
                lastPlayerY[playerId] = position.y;
            }
            return (axis.equals("x") ? lastPlayerX[playerId] : lastPlayerY[playerId]) + offset;
        }

        public String getAxis() {
            return axis;
        }

        public float getOffset() {
            return offset;
        }

        @Override
        public com.xam.bobgame.ai.trees.GameState<E> copyTo(com.xam.bobgame.ai.trees.GameState<E> gameState) {
            PlayerPositionState<E> other = (PlayerPositionState<E>) gameState;
            other.engine = engine;
            other.axis = axis;
            other.offset = offset;
            return gameState;
        }

        @Override
        public void reset() {
            super.reset();
            engine = null;
            axis = null;
        }
    }
}
