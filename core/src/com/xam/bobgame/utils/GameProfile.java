package com.xam.bobgame.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.XmlReader;
import com.badlogic.gdx.utils.XmlWriter;

import java.io.IOException;

public class GameProfile {
    public static final String SETTINGS_DIRPATH = ".xam/bobgame";
    public static final String PROFILE_PATH = SETTINGS_DIRPATH + "/" + "Profile.xml";

    public static String lastConnectedServerAddress = "";

    public static void load() {
        FileHandle fh = Gdx.files.external(PROFILE_PATH);
        if (!fh.exists()) return;
        XmlReader reader = new XmlReader();
        XmlReader.Element xml = reader.parse(fh);
        lastConnectedServerAddress = xml.get("lastConnectedServerAddress", lastConnectedServerAddress);
    }

    public static void save() {
        FileHandle fh = Gdx.files.external(PROFILE_PATH);
        XmlWriter writer = new XmlWriter(fh.writer(false));
        try {
            writer.element("Profile");
            writer.element("lastConnectedServerAddress", lastConnectedServerAddress);
            writer.pop();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
