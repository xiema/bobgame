package com.xam.bobgame.buffs;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Null;
import com.badlogic.gdx.utils.Pool.Poolable;
import com.badlogic.gdx.utils.Pools;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.events.classes.BuffEndedEvent;
import com.xam.bobgame.events.classes.BuffStartedEvent;
import com.xam.bobgame.events.EventsSystem;
import com.xam.bobgame.graphics.GraphicsRenderer;
import com.xam.bobgame.graphics.animators.Animator;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.net.NetSerializable;
import com.xam.bobgame.utils.BitPacker;

/**
 * Instance of a particular "Buff" on an entity.
 */
public class Buff implements NetSerializable, Poolable {
    public Entity owner;
    public int ownerEntityId = -1;
    public BuffDef buffDef;
    public float duration;
    public float accumulator;
    public float tickAccumulator;
    public boolean initialized = false;
    public boolean timingOut = false;
//    public GraphicAttachment graphicAttachment;
    public Array<Animator<GraphicsComponent>> animators = new Array<>(false, 4);

    @Override
    public int read(BitPacker packer, Engine engine) {
        ownerEntityId = packer.readInt(ownerEntityId, -1, NetDriver.MAX_ENTITY_ID - 1);

        if (packer.isWriteMode()) {
            packer.packInt(BuffDefs.getBuffDefIndex(buffDef), 0, BuffDefs.buffDefs.length - 1);
        }
        else {
            owner = ((GameEngine) engine).getEntityById(ownerEntityId);
            buffDef = BuffDefs.buffDefs[packer.unpackInt(0, BuffDefs.buffDefs.length - 1)];
        }

        duration = packer.readFloat(duration, 0, NetDriver.MAX_BUFF_DURATION, NetDriver.RES_BUFF_DURATION);
        accumulator = packer.readFloat(accumulator, 0, NetDriver.MAX_BUFF_DURATION, NetDriver.RES_BUFF_DURATION);
        tickAccumulator = packer.readFloat(tickAccumulator, 0, NetDriver.MAX_BUFF_DURATION, NetDriver.RES_BUFF_DURATION);
        initialized = packer.readBoolean(initialized);
        timingOut = packer.readBoolean(timingOut);

        if (packer.isWriteMode()) {
            packer.packInt(animators.size, 0, NetDriver.GRAPHICS_MAX_ANIMATORS);
            for (Animator<?> animator : animators) {
                packer.packInt(GraphicsRenderer.getAnimatorClassIndex(animator.getClass()), 0, GraphicsRenderer.animatorClasses.length - 1);
                animator.read(packer, engine);
            }
        }
        else {
            int count = packer.unpackInt(0, NetDriver.GRAPHICS_MAX_ANIMATORS);
            int i;
            for (i = 0; i < count; ++i) {
                Class<? extends Animator> clazz = (Class<? extends Animator>) GraphicsRenderer.animatorClasses[packer.unpackInt(0, GraphicsRenderer.animatorClasses.length - 1)];
                Animator<GraphicsComponent> animator;
                if (i >= animators.size) {
                    animator = Pools.obtain(clazz);
                    animators.add(animator);
                }
                else {
                    animator = animators.get(i);
                    if (animator.getClass() != clazz) {
                        Pools.free(animator);
                        animator = Pools.obtain(clazz);
                        animators.set(i, animator);
                    }
                }
                animator.read(packer, engine);
            }
            while (i < animators.size) {
                Pools.free(animators.get(i));
                animators.removeIndex(i);
            }
        }


        return 0;
    }

    /**
     * Initializer method
     */
    public void set(@Null Entity owner, BuffDef buffDef, float duration) {
        this.owner = owner;
        if (owner != null) ownerEntityId = ComponentMappers.identity.get(owner).id;
        this.buffDef = buffDef;
        this.duration = duration;
    }

    public void update(Engine engine, Entity receiver, float deltaTime) {
        if (!isFinished()) {
            // Begin effects
            if (!initialized) {
                initialized = true;
                buffDef.apply(engine, this, receiver);
                // update graphics
//                if (buffDefinition.spriteModel != null) {
//                    GraphicsComponent graphics = ComponentMappers.graphics.get(receiver);
//                    graphicAttachment = GraphicsSystem.createGraphicAttachment(
//                            buffDefinition.spriteModel, graphics.graphic.getWidth(), graphics.graphic.getHeight(), 0, 1.0f);
//                    for (AnimationEvent animationEvent : graphicAttachment.events) {
//                        animationEvent.entity = receiver;
//                        animationEvent.entityId = ComponentMappers.identity.get(receiver).id;
//                        engine.getSystem(EventsSystem.class).queueEvent(animationEvent);
//                    }
//                    graphicAttachment.events.clear();
//                    graphics.graphicAttachments.add(graphicAttachment);
//                    PositionComponent position = ComponentMappers.position.get(receiver);
//                    graphicAttachment.updateLocation(position.vector.x, position.vector.y, position.orientation);
//                }

                // send events
                BuffStartedEvent event = Pools.obtain(BuffStartedEvent.class);
                event.entity = receiver;
                event.entityId = ComponentMappers.identity.get(receiver).id;
                event.buff = this;
                engine.getSystem(EventsSystem.class).queueEvent(event);
                tickAccumulator = buffDef.secondsPerTick;

                // don't increment accumulator on first update
            }
            else {
                accumulator += deltaTime;
                tickAccumulator += deltaTime;
            }

            // Update effects (tick)
            if (tickAccumulator >= buffDef.secondsPerTick) {
                tickAccumulator %= buffDef.secondsPerTick;
                buffDef.tick(engine, this, receiver);
            }

            // "timing out" animation
            if (!timingOut && duration - accumulator < buffDef.timingOutThreshold) {
                // TODO: send event
                timingOut = true;
//                if (graphicAttachment != null) {
//                    graphicAttachment.setCurrentAnimation("TimingOut");
//                }
            }
        }
    }

    /**
     * End a Buff's effects on an entity. For stability, all buffs should call this before they are removed from the
     * game, including when an entity is removed before all buffs on it have ended.
     */
    public void endBuffEffects(Engine engine, Entity receiver) {
        buffDef.unapply(engine, this, receiver);
//        if (graphicAttachment != null) {
//            ComponentMappers.graphics.get(receiver).graphicAttachments.removeValue(graphicAttachment, true);
//            Pools.free(graphicAttachment);
//            graphicAttachment = null;
//        }

        // send events
        BuffEndedEvent event = Pools.obtain(BuffEndedEvent.class);
        event.entity = receiver;
        event.entityId = ComponentMappers.identity.get(receiver).id;
        event.buff = this;
        engine.getSystem(EventsSystem.class).queueEvent(event);
    }

    public boolean isFinished() {
        return accumulator >= duration;
    }

    @Override
    public void reset() {
        owner = null;
        ownerEntityId = -1;
        buffDef = null;
        initialized = false;
//        graphicAttachment = null;
        accumulator = 0;
        tickAccumulator = 0;
        timingOut = false;
        for (Animator<?> animator :animators) Pools.free(animator);
        animators.clear();
    }
}
