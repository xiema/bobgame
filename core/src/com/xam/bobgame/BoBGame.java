package com.xam.bobgame;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.XmlReader;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.xam.bobgame.definitions.GameDefinitions;
import com.xam.bobgame.dev.DevTools;
import com.xam.bobgame.graphics.GraphicsRenderer;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.ui.UIStage;
import com.xam.bobgame.utils.DebugUtils;
import com.xam.bobgame.utils.GameProfile;
import com.xam.bobgame.utils.HeadlessCommandRunnable;

import java.util.Map;

public class BoBGame extends ApplicationAdapter {
	int mode = 0;

	static boolean headless = false;

	GameEngine engine;
	NetDriver netDriver;
	GameDefinitions gameDefinitions;

	SpriteBatch batch;
	GraphicsRenderer renderer;
	Viewport viewport;
	Stage stage;

	UIStage uiStage;
	Viewport uiViewport;

	Skin skin;

	DevTools devTools;

	Thread headlessCommandThread;

	public BoBGame() {
		this(null);
	}

	public BoBGame(Map<String, String> runArgs) {
		if (runArgs != null) {
			if (runArgs.containsKey("server")) {
				mode = 1;
			}
			else if (runArgs.containsKey("client")) {
				mode = 2;
			}
			headless = runArgs.containsKey("headless");
		}
	}
	
	@Override
	public void create () {
		engine = new GameEngine(this);
		gameDefinitions = new GameDefinitions();
		gameDefinitions.createDefinitions(false);
		engine.initialize();

		GameProfile.load();

		if (!headless) {
			batch = new SpriteBatch();
			viewport = new FitViewport(GameProperties.MAP_WIDTH, GameProperties.MAP_HEIGHT);
			stage = new Stage(viewport, batch);
			renderer = new GraphicsRenderer(engine, stage);
			InputMultiplexer input = new InputMultiplexer();
			engine.addInputProcessor(input, viewport);
			Gdx.input.setInputProcessor(input);

			skin = new Skin(Gdx.files.internal("skin/uiskin.json"));
			uiViewport = new FitViewport(GameProperties.WINDOW_WIDTH, GameProperties.WINDOW_HEIGHT);
			uiStage = new UIStage(this, uiViewport, batch, skin);
			uiStage.initialize(engine);

			devTools = new DevTools(this);
			devTools.loadUI();
		}
		else {
			headlessCommandThread = new Thread(new HeadlessCommandRunnable(this));
			headlessCommandThread.start();
		}

		netDriver = engine.getSystem(NetDriver.class);

		engine.setMode(mode == 1 ? NetDriver.Mode.Server : NetDriver.Mode.Client);
	}

	@Override
	public void render () {
		engine.update(GameProperties.SIMULATION_UPDATE_INTERVAL);

		if (!headless) {
			stage.act(GameProperties.SIMULATION_UPDATE_INTERVAL);
			ScreenUtils.clear(0, 0, 0, 1);
			viewport.apply(true);
			renderer.draw(batch);

			uiStage.act(GameProperties.SIMULATION_UPDATE_INTERVAL);
			uiViewport.apply(true);
			uiStage.draw();

			devTools.render(GameProperties.SIMULATION_UPDATE_INTERVAL);
		}
	}

	@Override
	public void resize(int width, int height) {
		viewport.update(width, height);
		devTools.resize(width, height);
	}

	@Override
	public void dispose () {
		GameProfile.save();
		devTools.saveSettings();
		batch.dispose();
		netDriver.stop();
		if (headlessCommandThread != null) headlessCommandThread.interrupt();
	}

	public void onEngineStarted() {
		uiStage.initialize(engine);
	}

	public GameEngine getEngine() {
		return engine;
	}

	public Skin getSkin() {
		return skin;
	}

	public Viewport getWorldViewport() {
		return viewport;
	}

	public static boolean isHeadless() {
		return headless;
	}
}
