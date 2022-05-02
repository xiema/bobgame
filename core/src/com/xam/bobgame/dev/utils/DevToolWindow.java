package com.xam.bobgame.dev.utils;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.scenes.scene2d.EventListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.XmlReader;
import com.badlogic.gdx.utils.XmlWriter;
import com.esotericsoftware.minlog.Log;

import java.io.IOException;


/**
 * The base class for all tool windows, subclassed from the scene2D Window class.
 * Contains convenient UI-building functions and functions for loading and saving tool window attributes like position
 * and size.
 */
public class DevToolWindow extends Window {

    /**
     * Name/title that identifies this window. Also intended to be used internally to identify the window by
     * string.
     */
    public String windowTitle;
    public Button closeButton;

    public Engine engine;

    public DevToolWindow(String title, Skin skin) {
        super(title, skin);
        windowTitle = title;
        setWindowDefaults();
    }

    public DevToolWindow(String title, Skin skin, String styleName) {
        super(title, skin, styleName);
        windowTitle = title;
        setWindowDefaults();
    }

    public DevToolWindow(String title, WindowStyle style) {
        super(title, style);
        windowTitle = title;
        setWindowDefaults();
    }

    private void setWindowDefaults() {
        // set the default padding (should probably make these into constants)
        pad(36f, 8f, 4f, 8f);
        // align to top
        align(Align.top);
        // make all windows resizable by default
        setResizable(true);

        closeButton = new Button(getSkin(), "close");
        getTitleTable().add(closeButton);
        closeButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                setVisible(false);
            }
        });
    }

    protected void enableCloseButton(boolean value) {
        closeButton.setVisible(value);
    }

    /**
     * Loads and sets this window's position, size and visibility from settings saved in an xml element. This should
     * typically be called upon instantiating the window.
     */
    public void loadWindowSettings (XmlReader.Element xml, float defaultX, float defaultY, float defaultWidth, float defaultHeight, boolean defaultVisibility) {
        if (xml != null && xml.hasChild(windowTitle)) {
            XmlReader.Element settings = xml.getChildByName(windowTitle);
            applyWindowSettings(
                    settings.getFloatAttribute("x", defaultX),
                    settings.getFloatAttribute("y", defaultY),
                    settings.getFloatAttribute("w", defaultWidth),
                    settings.getFloatAttribute("h", defaultHeight),
                    settings.getBooleanAttribute("visible", defaultVisibility)
            );
        }
        else {
            applyWindowSettings(defaultX, defaultY, defaultWidth, defaultHeight, defaultVisibility);
        }
    }
    public void loadWindowSettings (XmlReader.Element xml, float defaultX, float defaultY, float defaultWidth, float defaultHeight) {
        loadWindowSettings(xml, defaultX, defaultY, defaultWidth, defaultHeight, true);
    }

    public void applyWindowSettings (float x, float y, float w, float h, boolean visible) {
        setBounds(x, y, w, h);
        setVisible(visible);
    }

    public void saveWindowSettings (XmlWriter xml) throws IOException {
        xml.element(windowTitle)
            .attribute("x", getX())
            .attribute("y", getY())
            .attribute("w", getWidth())
            .attribute("h", getHeight())
            .attribute("visible", isVisible());
    }

    public void onAddedToEngine(Engine engine) {
        this.engine = engine;
    }

    /**
     * Adds a new Label element using the default style in the next cell position.
     * Returns the newly created element for convenience.
     */
    public Label addLabelCell (CharSequence text) {
        Label label = new Label(text, getSkin());
        add(label);
        return label;
    }
    public Label addLabelCell (CharSequence text, String styleName) {
        Label label = new Label(text, getSkin(), styleName);
        add(label);
        return label;
    }

    /**
     * Adds a new TextField element using the default style in the next cell position.
     * Returns the newly created element for convenience.
     */
    public TextField addTextFieldCell (String text) {
        TextField textField = new TextField(text, getSkin());
        add(textField);
        return textField;
    }

    /**
     * Adds a new TextField element using the default style in the next cell position and attaches the given listener.
     * Returns the newly created element for convenience.
     */
    public TextField addTextFieldCell (String text, TextField.TextFieldListener listener) {
        TextField textField = addTextFieldCell(text);
        textField.setTextFieldListener(listener);
        return textField;
    }

    /**
     * Adds a new TextButton element using the default style in the next cell position and attaches the given listener.
     * Returns the newly created element for convenience.
     */
    public TextButton addTextButton (String text, EventListener listener) {
        TextButton textButton = addTextButton(text);
        textButton.addListener(listener);
        return textButton;
    }

    /**
     * Adds a new TextButton element using the given style in the next cell position and attaches the given listener.
     * Returns the newly created element for convenience.
     */
    public TextButton addTextButton (String text, String styleName, EventListener listener) {
        TextButton textButton = new TextButton(text, getSkin(), styleName);
        textButton.addListener(listener);
        add(textButton);
        return textButton;
    }

    /**
     * Adds a new TextButton element using the default style in the next cell position.
     * Returns the newly created element for convenience.
     */
    public TextButton addTextButton (String text) {
        TextButton textButton = new TextButton(text, getSkin());
        add(textButton);
        return textButton;
    }

    /**
     * Adds a new TextButton element using the given style in the next cell position.
     * Returns the newly created element for convenience.
     */
    public TextButton addTextButton (String text, String styleName) {
        TextButton textButton = new TextButton(text, getSkin(), styleName);
        add(textButton);
        return textButton;
    }

    /**
     * Adds a new SelectBox element of a specified type in the next cell position using the default style.
     */
    public <T> SelectBox<T> addSelectBox (Class<T> type) {
        SelectBox<T> selectBox = new SelectBox<>(getSkin());
        add(selectBox);
        return selectBox;
    }

    /**
     * Adds a new SelectBox element of a specified type in the next cell position using the default style, and attaches the
     * given EventListener.
     */
    public <T> SelectBox<T> addSelectBox (Class<T> type, EventListener listener) {
        SelectBox<T> selectBox = addSelectBox(type);
        selectBox.addListener(listener);
        return selectBox;
    }

    /**
     * Adds a new String-type SelectBox element in the next cell position using the default style.
     */
    public SelectBox<String> addSelectBox () {
        return addSelectBox(String.class);
    }

    /**
     * Adds a new String-type SelectBox element in the next cell position using the default style, and attaches the
     * given EventListener.
     */
    public SelectBox<String> addSelectBox (EventListener listener) {
        SelectBox<String> selectBox = addSelectBox(String.class, listener);
        return selectBox;
    }

    /**
     * Directly callable logging functions for convenience
     */
    public void log (String message) {
        Log.info(windowTitle, message);
    }
    public void debug (String message) {
        Log.debug(windowTitle, message);
    }
    public void error (String message) {
        Log.error(windowTitle, message);
    }
}
