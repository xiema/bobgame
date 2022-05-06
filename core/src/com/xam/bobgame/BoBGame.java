package com.xam.bobgame;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.definitions.GameDefinitions;
import com.xam.bobgame.dev.DevTools;
import com.xam.bobgame.graphics.GraphicsRenderer;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.ui.UIStage;
import com.xam.bobgame.utils.HeadlessCommandRunnable;

import java.util.Map;

public class BoBGame extends ApplicationAdapter {
	static boolean headless = false;
	static boolean devMode = false;
	static boolean noProfile = false;
	static boolean noUDP = false;

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
			headless = runArgs.containsKey("headless");
			devMode = runArgs.containsKey("devMode");
			noProfile = runArgs.containsKey("noProfile");
			noProfile = runArgs.containsKey("noProfile");
			noUDP = runArgs.containsKey("noUDP");
		}
		if (devMode) {
			Log.set(Log.LEVEL_DEBUG);
		}
	}
	
	@Override
	public void create () {
		GameProfile.load();

		engine = new GameEngine(this);
		gameDefinitions = new GameDefinitions();
		gameDefinitions.createDefinitions(false);
		engine.initialize();
		netDriver = engine.getSystem(NetDriver.class);

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

			// reconnection
			if (GameProfile.clientSalt != 0) {
				engine.setupClient();
			}

			uiStage.initialize(engine);

			if (devMode) {
				devTools = new DevTools(this);
				devTools.loadUI();
			}
		}
		else {
			headlessCommandThread = new Thread(new HeadlessCommandRunnable(this));
			headlessCommandThread.start();
			netDriver.startServer();
			engine.start();
		}
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

			if (devMode) devTools.render(GameProperties.SIMULATION_UPDATE_INTERVAL);
		}
	}

	@Override
	public void resize(int width, int height) {
		uiViewport.update(width, height);
		int w = uiViewport.getScreenWidth(), h = uiViewport.getScreenHeight();
		viewport.update(w, h);
		viewport.setScreenPosition(uiViewport.getScreenX() + (w - viewport.getScreenWidth()) / 2, uiViewport.getScreenY() + (h - viewport.getScreenHeight()) / 2);
		if (devMode) devTools.resize(width, height);
	}

	@Override
	public void dispose () {
		GameProfile.save();
		if (devMode) devTools.saveSettings();
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

	public static boolean isNoUDP() {
		return noUDP;
	}
}
