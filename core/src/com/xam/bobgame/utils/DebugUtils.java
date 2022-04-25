package com.xam.bobgame.utils;

import com.xam.bobgame.BoBGame;

public class DebugUtils {

    public static void log(String tag, String msg) {
        System.out.println("[" + BoBGame.getCounter() + "] " + tag + " : " + msg);
    }

    public static void error(String tag, String msg) {
        System.err.println("[" + BoBGame.getCounter() + "] " + tag + " :" + msg);
    }

    public static class ExpoMovingAverage {
        private float ave = 0;
        private boolean init = false;
        private float alpha;

        public ExpoMovingAverage(float alpha) {
            this.alpha = alpha;
        }

        public float update(float val) {
            if (init) {
                return ave = ave * (1 - alpha) + val * alpha;
            }
            else {
                init = true;
                return ave = val;
            }
        }

        public void setAlpha(float alpha) {
            this.alpha = alpha;
        }

        public void reset() {
            ave = 0;
        }
    }

    public static String bytesHex(byte[] bytes) {
        return bytesHex(bytes, 0, bytes.length);
    }

    public static String bytesHex(byte[] bytes, int i, int j) {
        StringBuilder sb = new StringBuilder();
        for (; i < j; ++i) {
            sb.append(Integer.toHexString(bytes[i]));
            sb.append(' ');
        }
        return sb.toString();
    }
}
