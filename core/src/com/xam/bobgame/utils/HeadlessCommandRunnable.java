package com.xam.bobgame.utils;

import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.BoBGame;
import com.xam.bobgame.net.NetDriver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Formatter;

public class HeadlessCommandRunnable implements Runnable {

    BoBGame game;

    public HeadlessCommandRunnable(BoBGame game) {
        this.game = game;
    }

    @Override
    public void run() {
        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            try {
                String input = br.readLine();
                NetDriver netDriver = game.getEngine().getSystem(NetDriver.class);
                Formatter f = new Formatter();
                Log.info(f.format("Bitrate recv=%1.2f send=%1.2f", netDriver.getAverageReceiveBitrate(), netDriver.getAverageSendBitrate()).toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
