package com.xam.bobgame.dev;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.XmlReader;
import com.badlogic.gdx.utils.XmlWriter;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.file.FileChooser;
import com.xam.bobgame.BoBGame;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.dev.tools.EntityInspector;
import com.xam.bobgame.dev.tools.QuickCommands;
import com.xam.bobgame.dev.tools.WindowManager;
import com.xam.bobgame.dev.utils.DevToolWindow;

import java.io.IOException;

/**
 * Collection of all hardcoded development tools, with access to a UI framework.
 * Uses scene2D for its simple and convenient graphics and UI libraries.
 */
public class DevTools {
    private static final String SETTINGS_DIRPATH = ".xam/bobgame";
    private static final String DEVSETTINGS_PATH = SETTINGS_DIRPATH + "/" + "DevSettings.xml";
    private static final String SKIN_PATH = "sgx-ui/sgx-ui.json";
    private static final String SKIN_ATLAS_PATH = "sgx-ui/sgx-ui.atlas";
    private static final XmlReader xmlReader = new XmlReader();

    public static final Skin skin = new Skin();

    public BoBGame game;
    public GameEngine engine;
    public DevToolsSystem devToolsSystem;
    private boolean isLoaded = false;

//    public DevToolsSystem devToolsSystem;
    public Stage devUIStage;
    public Batch batch;
    public Label gameVersionLabel;
    public Label fpsLabel;

    public static FileChooser _fileChooser;

    public Camera camera;
    public Viewport viewport;

    /** Array for all DevTool windows except the Window Manager
     */
    public Array<DevToolWindow> devToolWindows = new Array<>();
    public DevToolWindow windowManager;

    /**
     * Constructor. Any non-static objects that must be accessed by tools should be passed here.
     * @param game Main Game object
     */
    public DevTools(BoBGame game) {
        this.game = game;
        engine = game.getEngine();

        camera = new OrthographicCamera(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        viewport = new FitViewport(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), camera);

        batch = new SpriteBatch();
        devUIStage = new Stage(viewport, batch);

        devToolsSystem = new DevToolsSystem(this, devUIStage);

        // initialize resources
        skin.addRegions(new TextureAtlas(SKIN_ATLAS_PATH));
        skin.load(Gdx.files.internal(SKIN_PATH));

        ((InputMultiplexer) Gdx.input.getInputProcessor()).addProcessor(devUIStage);
    }

    public void loadUI () {
        if (!isLoaded) {
//            game.getAssetManager().load(SKIN_PATH, Skin.class);
//            game.getAssetManager().finishLoading();

            VisUI.load();

            // prep windows
//            devToolWindows.add(new EntityInfo(this, skin));
            devToolWindows.add(new EntityInspector(this, skin));
            devToolWindows.add(new QuickCommands(this, skin));
            for (DevToolWindow devToolWindow : devToolWindows) {
                devUIStage.addActor(devToolWindow);
            }
            windowManager = new WindowManager(this, skin);

            // other ui elements
//            gameVersionLabel = new Label(AstrodemicGame.getGameVersion(), skin);
//            gameVersionLabel.setPosition(viewport.getWorldWidth(), 0, Align.bottomRight);
            fpsLabel = new Label("FPS:00", skin);
            fpsLabel.setPosition(viewport.getWorldWidth(), viewport.getWorldHeight(), Align.topRight);

            // default visible
            devUIStage.addActor(windowManager);
//            devUIStage.addActor(gameVersionLabel);
            devUIStage.addActor(fpsLabel);

            isLoaded = true;
        }
    }

    public void unloadUI () {
        devUIStage.clear();
    }

    public void render (float deltaTime) {
        // add DevToolsSystem if not added yet
        if (engine.getSystem(DevToolsSystem.class) == null) {
            engine.addSystem(devToolsSystem);
        }

        fpsLabel.setText("FPS:" + Gdx.graphics.getFramesPerSecond());

        viewport.apply(true);
        devUIStage.act();
        devUIStage.draw();
        devToolsSystem.draw(batch);
    }

    public void onPause () {
        saveSettings();
    }

    public void onAddedToEngine(Engine engine) {
        for (DevToolWindow window : devToolWindows) {
            window.onAddedToEngine(engine);
        }
    }

    public XmlReader.Element getWindowSettingsXML () {
        FileHandle fileHandle;
        XmlReader.Element xml, settings;

        fileHandle = Gdx.files.external(DEVSETTINGS_PATH);
        if (fileHandle.exists()) {
            xml = xmlReader.parse(fileHandle);
            if (xml != null) {
                return xml.getChildByName("Windows");
            }
        }
        return null;
    }

    public void saveSettings () {
        FileHandle fileHandle;

        fileHandle = Gdx.files.external(DEVSETTINGS_PATH);
        fileHandle.writeString("", false); // clear
        XmlWriter xml = new XmlWriter(fileHandle.writer(true));

        try {
            xml.element("DevSettings").element("Windows");
            for (DevToolWindow tool : devToolWindows) {
                tool.saveWindowSettings(xml);
                xml.pop();
            }
            windowManager.saveWindowSettings(xml);
            xml.pop();
            xml.close();
        }
        catch (IOException e) {
            e.printStackTrace();
//            Debug.error("GameSettings", "Failed writing settings to file");
        }
    }

    public void hide() {
        saveSettings();
    }

    public void resize(int width, int height) {
        if (width == 0 && height == 0) return;
        viewport.setWorldSize(width, height);
        viewport.update(width, height);
    }

    public FileChooser getFileChooser(FileChooser.Mode mode) {
        if (_fileChooser == null) {
            FileChooser.setDefaultPrefsName("com.devgames.astrodemic.filechooser");
            _fileChooser = new FileChooser(mode);
        }
        _fileChooser.setMode(mode);
        return _fileChooser;
    }
}
