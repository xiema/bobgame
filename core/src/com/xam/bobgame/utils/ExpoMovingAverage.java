package com.xam.bobgame.utils;

public class ExpoMovingAverage {
    private float ave = 0;
    private boolean init = false;
    private float alpha;

    public ExpoMovingAverage(float alpha) {
        this.alpha = alpha;
    }

    public float update(float val) {
        if (init) {
            return ave = ave * (1 - alpha) + val * alpha;
        } else {
            init = true;
            return ave = val;
        }
    }

    public float getAverage() {
        return ave;
    }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public boolean isInit() {
        return init;
    }

    public void reset() {
        reset(0);
    }

    public void reset(float val) {
        ave = val;
    }
}
