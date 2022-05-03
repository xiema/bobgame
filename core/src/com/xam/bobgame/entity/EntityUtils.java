package com.xam.bobgame.entity;

import com.badlogic.ashley.core.*;
import com.badlogic.gdx.utils.ObjectMap;
import com.xam.bobgame.components.IdentityComponent;

public class EntityUtils {

    public static int getId(Entity entity) {
        if (entity == null) return -1;
        IdentityComponent iden = ComponentMappers.identity.get(entity);
        if (iden == null) return -1;
        return iden.id;
    }

    public static void addEntityListeners(Engine engine, ObjectMap<Family, EntityListener> listeners) {
        for (ObjectMap.Entry<Family, EntityListener> entry : listeners.entries()) {
            engine.addEntityListener(entry.key, entry.value);
        }
    }

    public static void removeEntityListeners(Engine engine, ObjectMap<Family, EntityListener> listeners) {
        for (ObjectMap.Entry<Family, EntityListener> entry : listeners.entries()) {
            engine.removeEntityListener(entry.value);
        }
    }
}
