package com.xam.bobgame.utils;

import com.badlogic.gdx.utils.IntArray;
import com.badlogic.gdx.utils.IntMap;

import java.util.Arrays;
import java.util.NoSuchElementException;

public class OrderedIntMap<V> extends IntMap<V> {

    final IntArray keys;

    public OrderedIntMap() {
        keys = new IntArray();
    }

    public V put(int key, V value) {
        V old = super.put(key, value);
        if (old == null) {
            int i = Arrays.binarySearch(keys.items, 0, keys.size, key);
            keys.insert(-(i + 1), key);
        }
        return old;
    }

    @Override
    public V remove(int key) {
        V old = super.remove(key);
        if (old != null) {
            int i = Arrays.binarySearch(keys.items, 0, keys.size, key);
            if (i >= 0) {
                keys.removeIndex(i);
            }
            else {
                throw new NoSuchElementException();
            }
        }
        return old;
    }

    public int getKey(int index) {
        return keys.get(index);
    }

    @Override
    public void clear() {
        super.clear();
        keys.clear();
    }
}
