package com.xam.bobgame;

public class GameProperties {
    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 720;
    public static final int FORCE_METER_WIDTH = 50;
    public static final int FORCE_METER_HEIGHT = WINDOW_HEIGHT;
    public static final int MENU_WIDTH = WINDOW_WIDTH - WINDOW_HEIGHT - FORCE_METER_WIDTH;

    public static final float MAP_WIDTH = 20f;
    public static final float MAP_HEIGHT = 20f;

    public static final float START_X = 10f;
    public static final float START_Y = 10f;

    public static final int Z_POS_MAX = 3;

    public static final int MAX_PLAYERS = 16;
    public static final float MATCH_TIME = 60;

    public static final float PLAYER_FORCE_STRENGTH = 1000f;
    public static final float LINEAR_DAMPENING = 0.5f;
    public static final float CHARGE_DURATION = 0.4f;
    public static final float CHARGE_DURATION_2 = CHARGE_DURATION * 2;

    public static final int PLAYER_DEATH_POINT_PENALTY = -3;

    public static final float PICKUP_SPAWN_COOLDOWN = 3f;
    public static final float PICKUP_PUSH_STRENGTH = 80f;
    public static final float PICKUP_MIN_ANGULAR_VEL = -6.28f;
    public static final float PICKUP_MAX_ANGULAR_VEL = 6.28f;

    public static final float PLAYER_RESPAWN_TIME = 5;
    public static final float PLAYER_SPAWN_MARGIN = 3;
    public static final float PLAYER_STAMINA_LOSS = 48;
    public static final float PLAYER_STAMINA_RECOVERY = 13;
    public static final float PLAYER_STAMINA_MIN = 30;
    public static final float PLAYER_STAMINA_MAX = 100;
    public static final float CHARGE_RATE = PLAYER_STAMINA_MAX / CHARGE_DURATION;

    public static final float SIMULATION_UPDATE_INTERVAL = 1f / 60f;
    public static final long SIMULATION_UPDATE_INTERVAL_L = (long) (SIMULATION_UPDATE_INTERVAL * 1000);

    public static final int BITRATE_INDICATOR_UPDATE_INTERVAL = 3;

    public static final String WINDOW_TITLE = "Battle of Balls";
    public static final String REPOSITORY_URL = "github.com/xiema/bobgame";
}
