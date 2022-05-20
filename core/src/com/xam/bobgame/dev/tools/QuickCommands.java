package com.xam.bobgame.dev.tools;

import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.xam.bobgame.dev.DevTools;
import com.xam.bobgame.dev.utils.DevToolWindow;
import com.xam.bobgame.net.ConnectionManager;
import com.xam.bobgame.net.NetDriver;

public class QuickCommands extends DevToolWindow {

    TextButton simulateDisconnect;

    public QuickCommands(final DevTools devTools, Skin skin) {
        super("QuickCommands", skin);

        // set default properties of new window elements:
        //   left-aligned, expand cells to size of window, expand content to size of cell
        defaults().left().expandX().fillX();

        simulateDisconnect = new TextButton("Sim. Disconnect", skin);
        simulateDisconnect.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                ConnectionManager connectionManager = devTools.engine.getSystem(NetDriver.class).getConnectionManager();
                ConnectionManager.ConnectionSlot slot = connectionManager.getConnectionSlot(0);
                if (slot != null) slot.getConnection().close();
            }
        });
        add(simulateDisconnect);

        // load saved window settings, or use specified defaults
        loadWindowSettings(devTools.getWindowSettingsXML(), 0f, 0f, getMinWidth(), getMinHeight(), true);
    }
}
