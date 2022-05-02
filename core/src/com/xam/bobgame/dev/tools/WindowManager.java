package com.xam.bobgame.dev.tools;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Event;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.xam.bobgame.dev.DevTools;
import com.xam.bobgame.dev.utils.DevToolWindow;

/**
 * Tool for managing window settings of other devtools.
 */
public class WindowManager extends DevToolWindow {
    public DevTools devTools;

    public TextButton[] windows;

    public WindowManager(final DevTools devTools, Skin skin) {
        super("WindowManager", skin);
        enableCloseButton(false);
        this.devTools = devTools;

        // set default properties of new window elements:
        //   centered, expand cells to size of window, expand content to size of cell, set padding
        defaults().center().expandX().fillX().pad(2f);

        // create TextButton elements for each devtool, labeled with the tool's windowTitle
        windows = new TextButton[devTools.devToolWindows.size];
        int i = 0;
        for (DevToolWindow window : devTools.devToolWindows) {
            windows[i] = addTextButton(window.windowTitle);
            ++i;
            row();
        }

        // add a listener to this window that captures ChangeEvents and toggles the corresponding tool window
        addListener(new EventListener() {
            @Override
            public boolean handle(Event event) {
                Actor target = event.getTarget();
                if (event instanceof ChangeListener.ChangeEvent) {
                    for (int i = 0; i < windows.length; ++i) {
                        if (target == windows[i]) {
                            devTools.devToolWindows.get(i).setVisible(!devTools.devToolWindows.get(i).isVisible());
                        }
                    }
                }
                return true; //true means event has been handled
            }
        });

        loadWindowSettings(devTools.getWindowSettingsXML(), 0f, 0f, getMinWidth(), getMinHeight());
    }
}
