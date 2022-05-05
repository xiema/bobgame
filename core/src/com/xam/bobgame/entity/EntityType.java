package com.xam.bobgame.entity;

public enum EntityType {
    Player(0), Hazard(1), Pickup(2), Neutral(3),;

    private final int value;

    EntityType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
