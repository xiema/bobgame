package com.xam.bobgame;

public class GameProperties {
    public static final int WINDOW_WIDTH = 1000;
    public static final int WINDOW_HEIGHT = 500;

    public static final float MAP_WIDTH = 20f;
    public static final float MAP_HEIGHT = 20f;

    public static final float START_X = 10f;
    public static final float START_Y = 10f;

    public static final int Z_POS_MAX = 3;

    public static final float PLAYER_FORCE_STRENGTH = 1000f;
    public static final float LINEAR_DAMPENING = 0.5f;
    public static final float CHARGE_DURATION = 0.4f;
    public static final float CHARGE_DURATION_2 = CHARGE_DURATION * 2;

    public static final float SIMULATION_UPDATE_INTERVAL = 1f / 60f;
}
