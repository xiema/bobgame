package com.xam.bobgame.definitions;

import com.badlogic.gdx.utils.XmlReader;

/**
 * Wrapper class for {@link XmlReader.Element} used for handling of various definitions
 */
public class GameDefinition {
    public final XmlReader.Element xml;
    public final String id;
    protected GameDefinitions gameDefinitions;
    private boolean loaded;

    public GameDefinition(String id, XmlReader.Element xml, GameDefinitions gameDefinitions) {
        this.id = id;
        this.xml = xml;
        this.gameDefinitions = gameDefinitions;
        loaded = false;
    }

    public void load() throws GameDefinitions.UnloadedDefinitionException {
        if (!loaded) {
            onLoad();
            loaded = true;
        }
    }

    public void unload() {
        if (loaded) {
            onUnload();
            loaded = false;
        }
    }

    void onLoad() {
    }

    void onUnload() {
    }

    public String get(String name) {
        return xml.get(name);
    }

    public int getInt(String name) {
        return xml.getInt(name);
    }

    public float getFloat(String name) {
        return xml.getFloat(name);
    }

    public String getAttribute(String name) {
        return xml.getAttribute(name);
    }

    public int getIntAttribute(String name) {
        return xml.getIntAttribute(name);
    }

    public float getFloatAttribute(String name) {
        return xml.getFloatAttribute(name);
    }

    public boolean isLoaded() {
        return loaded;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + ":" + id;
    }
}
