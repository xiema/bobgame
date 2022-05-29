package com.xam.bobgame.graphics.animators;

import com.badlogic.gdx.graphics.Color;

public interface Animated {

    void setDrawTint(Color color);

    void setDrawTint(float r, float g, float b, float a);

    void setAlpha(float a);

    void setDrawOffsets(float x, float y, float orientation);

    Color getDrawTint();

    float getDrawX();

    float getDrawY();

    float getX();

    float getY();

    float getOriginX();

    float getOriginY();

    float getRotation();

    float getDrawOrientation();
}
