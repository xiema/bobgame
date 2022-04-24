package com.xam.bobgame.utils;

import com.xam.bobgame.BoBGame;

public class DebugUtils {

    public static void log(String tag, String msg) {
        System.out.println("[" + BoBGame.getCounter() + "] " + tag + " : " + msg);
    }

    public static void error(String tag, String msg) {
        System.err.println("[" + BoBGame.getCounter() + "] " + tag + " :" + msg);
    }
}
