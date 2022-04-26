package com.xam.bobgame.game;

import com.badlogic.gdx.physics.box2d.CircleShape;
import com.badlogic.gdx.physics.box2d.Shape;

public class ShapeDef {

    public ShapeType type = ShapeType.Circle;
    public float shapeVal1;

    public Shape createShape() {
        Shape shape = new CircleShape();
        shape.setRadius(shapeVal1);
        return shape;
    }

    public enum ShapeType {
        Circle(0);

        private int value;

        ShapeType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }
}
