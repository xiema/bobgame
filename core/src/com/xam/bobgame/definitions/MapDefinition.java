package com.xam.bobgame.definitions;

import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.XmlReader;
import com.esotericsoftware.minlog.Log;

public class MapDefinition extends GameDefinition {

    public final ImmutableArray<Wall> walls;

    public MapDefinition(XmlReader.Element xml, GameDefinitions gameDefinitions) {
        super(xml.get("id"), xml, gameDefinitions);

        Array<Wall> walls = new Array<>();
        for (XmlReader.Element child : xml.getChildByName("Walls").getChildrenByName("Wall")) {
            float x = child.getFloatAttribute("x");
            float y = child.getFloatAttribute("y");
            float w = child.getFloatAttribute("w");
            float h = child.getFloatAttribute("h");
            walls.add(new Wall(x, y, w, h));
        }

        this.walls = new ImmutableArray<>(walls);
    }

    public static class Wall {

        public final float x, y, w, h;

        public Wall(float x, float y, float w, float h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }
}
