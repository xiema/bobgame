package com.xam.bobgame.buffs;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;

public class BuffDefs {

    public static final BuffDef SpawnInvBuffDef = new BuffDef(
            "SpawnInv", 3, 1, 3, false) {
        @Override
        public void apply(Engine engine, Buff buff, Entity entity) {

        }

        @Override
        public void tick(Engine engine, Buff buff, Entity entity) {

        }

        @Override
        public void unapply(Engine engine, Buff buff, Entity entity) {

        }
    };
}
