package com.xam.bobgame.buffs;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.utils.Pools;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.entity.ComponentMappers;
import com.xam.bobgame.graphics.animators.BlinkAnimator;

public class BuffDefs {

    public static final BuffDef SpawnInvBuffDef = new BuffDef(
            "SpawnInv", 3, 1, 3, false) {
        @Override
        public void apply(Engine engine, Buff buff, Entity entity) {
            GraphicsComponent graphics = ComponentMappers.graphics.get(entity);
            if (graphics == null) return;
            BlinkAnimator<GraphicsComponent> animator = Pools.obtain(BlinkAnimator.class);
            animator.set(graphics, 0.1f, 0.35f, 1);
            buff.animators.add(animator);
        }

        @Override
        public void tick(Engine engine, Buff buff, Entity entity) {

        }

        @Override
        public void unapply(Engine engine, Buff buff, Entity entity) {
            GraphicsComponent graphics = ComponentMappers.graphics.get(entity);
            if (graphics == null) return;
            for (int i = 0; i < buff.animators.size; ++i) {
                if (buff.animators.get(i) instanceof BlinkAnimator) {
                    buff.animators.get(i).setFinished(true);
                }
            }
        }
    };

    public static BuffDef[] buffDefs = {
            SpawnInvBuffDef,
    };

    public static int getBuffDefIndex(BuffDef buffDef) {
        for (int i = 0; i < buffDefs.length; ++i) {
            if (buffDefs[i] == buffDef) return i;
        }
        return -1;
    }
}
