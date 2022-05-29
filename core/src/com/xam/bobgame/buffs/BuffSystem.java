package com.xam.bobgame.buffs;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Null;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pools;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.components.BuffableComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.events.*;

public class BuffSystem extends EntitySystem {

    private ImmutableArray<Entity> buffableEntities;

    private ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();
    private ObjectMap<Family, EntityListener> entityListeners = new ObjectMap<>();

    public BuffSystem(int priority) {
        super(priority);

        entityListeners.put(Family.all(BuffableComponent.class).get(), new EntityListener() {
            @Override
            public void entityAdded(Entity entity) {

            }

            @Override
            public void entityRemoved(Entity entity) {
                removeBuffs(entity);
            }
        });
    }

    @Override
    public void addedToEngine(Engine engine) {
        buffableEntities = engine.getEntitiesFor(Family.all(BuffableComponent.class).get());
//        engine.getSystem(EventsSystem.class).addListeners(listeners);
        for (ObjectMap.Entry<Family, EntityListener> entry : entityListeners) {
            engine.addEntityListener(entry.key, entry.value);
        }
    }

    @Override
    public void removedFromEngine(Engine engine) {
        buffableEntities = null;
//        EventsSystem eventsSystem = engine.getSystem(EventsSystem.class);
//        if (eventsSystem != null) eventsSystem.removeListeners(listeners);
        for (ObjectMap.Entry<Family, EntityListener> entry : entityListeners) {
            engine.removeEntityListener(entry.value);
        }
    }

    @Override
    public void update(float deltaTime) {
        Engine engine = getEngine();

        // Update buffs
        for (Entity entity : buffableEntities) {
            BuffableComponent buffable = ComponentMappers.buffables.get(entity);
            for (int i = 0; i < buffable.buffs.size; ) {
                Buff buff = buffable.buffs.get(i);
                buff.update(engine, entity, deltaTime);
                if (((GameEngine) getEngine()).getMode() == GameEngine.Mode.Server && buff.isFinished()) {
                    buffable.buffs.removeIndex(i);
                    buff.endBuffEffects(engine, entity);
                    Pools.free(buff);
                }
                else {
                    i++;
                }
            }
        }

        // Update auras
//        for (Entity entity : perkEntities) {
//            PerksComponent perks = ComponentMappers.perks.get(entity);
//            if (perks.disabled) continue;
//
//            Faction faction = ComponentMappers.identity.get(entity).faction;
//            PositionComponent position = ComponentMappers.position.get(entity);
//            if (!perks.aurasActiveOutOfPlayArea && !engine.getSystem(LevelDirector.class).isEntityWithinPlayArea(entity)) continue;
//
//            for (AuraDefinition aura : perks.auras) {
//                for (Entity otherEntity : buffableEntities) {
//                    Faction otherFaction = ComponentMappers.identity.get(otherEntity).faction;
//                    PositionComponent otherPosition = ComponentMappers.position.get(otherEntity);
//
//                    if ((aura.targetFriendly && faction == otherFaction && (aura.targetSelf || entity != otherEntity)) || (aura.targetHostile && faction != otherFaction)) {
//                        if (aura.global || MathUtils.isDistanceLessThan(position.vector.x, position.vector.y, otherPosition.vector.x, otherPosition.vector.y, aura.range)) {
//                            addBuff(otherEntity, entity, aura.buff, aura.duration);
//                        }
//                    }
//                }
//            }
//        }
    }


    // BUFF MANIPULATION

    public static void addBuff(Entity receiver, @Null Entity owner, BuffDef buffDef, float duration) {
        BuffableComponent buffable = ComponentMappers.buffables.get(receiver);

        // prevent stacking of unstackable buffs
        if (!buffDef.stackable) {
            for (Buff buff : buffable.buffs) {
                if (buff.buffDef == buffDef) {
                    buff.duration = Math.max(duration, buff.duration);
                    buff.accumulator = 0;
                    if (buff.timingOut) {
                        buff.timingOut = false;
//                        buff.graphicAttachment.setCurrentAnimation(SpriteModelDefinition.DEFAULT_ANIMATION_NAME);
                    }
                    return;
                }
            }
        }

        Buff buff = Pools.obtain(Buff.class);
        buff.set(owner, buffDef, duration);
        buffable.buffs.add(buff);
    }

    public static void removeBuff(Engine engine, Entity receiver, String buffId) {
        BuffableComponent buffable = ComponentMappers.buffables.get(receiver);

        for (int i = 0, n = buffable.buffs.size; i < n; ++i) {
            Buff buff = buffable.buffs.get(i);
            if (buff.buffDef.name.equals(buffId)) {
                buff.endBuffEffects(engine, receiver);
                buffable.buffs.removeIndex(i);
                return;
            }
        }

        Log.warn("BuffSystem.removeBuff", "Couldn't find buff to remove: " + buffId);
    }

    public void removeBuffs(Entity entity) {
        Engine engine = getEngine();
        BuffableComponent buffable = ComponentMappers.buffables.get(entity);
        if (buffable == null) return;
        while (buffable.buffs.size > 0) {
            Buff buff = buffable.buffs.removeIndex(0);
            buff.endBuffEffects(engine, entity);
            Pools.free(buff);
        }
        buffable.buffs.clear();
    }

    public static @Null Buff getBuff(Entity entity, BuffDef buffDef) {
        BuffableComponent buffable = ComponentMappers.buffables.get(entity);
        if (buffable != null) {
            for (Buff buff : buffable.buffs) {
                if (buff.buffDef == buffDef) {
                    return buff;
                }
            }
        }
        return null;
    }

    public static boolean hasBuff(Entity entity, BuffDef buffDef) {
        BuffableComponent buffable = ComponentMappers.buffables.get(entity);
        if (buffable != null) {
            for (Buff buff : buffable.buffs) {
                if (buff.buffDef == buffDef) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean hasBuffDef(BuffableComponent buffable, BuffDef buffDef) {
        for (Buff buff : buffable.buffs) {
            if (buff.buffDef == buffDef) return true;
        }
        return false;
    }

    public static boolean hasBuffDef(Entity entity, BuffDef buffDef) {
        if (entity == null) {
            Log.warn("BuffSystem", "Tried to check buff def for null entity");
            return false;
        }
        BuffableComponent buffable = ComponentMappers.buffables.get(entity);
        return hasBuffDef(buffable, buffDef);
    }
}
