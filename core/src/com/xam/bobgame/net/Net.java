package com.xam.bobgame.net;

public class Net {
    public static final int HEADER_WORDS = 2;
    public static final int DATA_MAX_WORDS = 128;
    public static final int SNAPSHOT_MAX_WORDS = 128;
    public static final int PACKET_MAX_WORDS = DATA_MAX_WORDS + HEADER_WORDS;
    public static final int DATA_MAX_SIZE = DATA_MAX_WORDS * 4;
    public static final int SNAPSHOT_MAX_SIZE = SNAPSHOT_MAX_WORDS * 4;
    public static final int HEADER_SIZE = HEADER_WORDS * 4;
    public static final int PACKET_MAX_SIZE = PACKET_MAX_WORDS * 4;

    public static final float BUFFER_TIME_LIMIT = 0.5f;
}
