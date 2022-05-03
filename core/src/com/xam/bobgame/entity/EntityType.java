package com.xam.bobgame.entity;

public enum EntityType {
    Player(0), Hazard(1), Neutral(2),;

    private final int value;

    EntityType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
