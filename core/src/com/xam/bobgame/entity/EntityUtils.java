package com.xam.bobgame.entity;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;

public class EntityUtils {

    public static int getId(Entity entity) {
        return entity != null ? ComponentMappers.identity.get(entity).id : -1;
    }
}
