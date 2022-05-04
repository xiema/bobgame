package com.xam.bobgame.ai;

import com.badlogic.gdx.utils.Null;
import com.badlogic.gdx.utils.ObjectMap;
import com.esotericsoftware.minlog.Log;

public class AIMemory {

    private ObjectMap<String, Object> map;

    public AIMemory() {
        map = new ObjectMap<>();
    }

    public void put(String name, Object object) {
        map.put(name, object);
    }

    public @Null <T> T get(String name, Class<T> type) {
        Object object = map.get(name);
        try {
            if (object != null) return type.cast(object);
        }
        catch (ClassCastException e) {
            Log.error("AIMemory", e.getMessage());
        }
        return null;
    }

    public String getString(String name, String defaultValue) {
        Object object = map.get(name);
        try {
            if (object != null) return (String) object;
        }
        catch (ClassCastException e) {
            Log.error("AIMemory", e.getMessage());
        }
        return null;
    }

    public int getInt(String name, int defaultValue) {
        Object object = map.get(name);
        try {
            if (object != null) return (int) object;
        }
        catch (ClassCastException e) {
            Log.error("AIMemory", e.getMessage());
        }
        return defaultValue;
    }

    public float getFloat(String name, float defaultValue) {
        Object object = map.get(name);
        try {
            if (object != null) return (float) object;
        }
        catch (ClassCastException e) {
            Log.error("AIMemory", e.getMessage());
        }
        return defaultValue;
    }

    public boolean getBoolean(String name, boolean defaultValue) {
        Object object = map.get(name);
        try {
            if (object != null) return (boolean) object;
        }
        catch (ClassCastException e) {
            Log.error("AIMemory", e.getMessage());
        }
        return defaultValue;
    }

    public void clear() {
        map.clear();
    }
}
