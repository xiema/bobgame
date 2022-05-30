package com.xam.bobgame;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Formatter;
import java.util.Map;

public class BoBGame extends ApplicationAdapter {
	static boolean headless = false;
	static boolean devMode = false;
	static boolean noProfile = false;
	static boolean noUDP = false;
	static int tcpPort = NetDriver.PORT_TCP;
	static int udpPort = NetDriver.PORT_UDP;

	GameEngine engine;
	GameDefinitions gameDefinitions;
	InputMultiplexer inputMultiplexer;

	SpriteBatch batch;
	GraphicsRenderer renderer;
	Viewport viewport;

	UIStage uiStage;
	Skin skin;

	DevTools devTools;

	Thread headlessCommandThread;
	Thread headlessEngineThread;
	BufferedReader commandReader = new BufferedReader(new InputStreamReader(System.in));

	public BoBGame() {
		this(null);
	}

	public BoBGame(Map<String, String> runArgs) {
		if (runArgs != null) {
			headless = runArgs.containsKey("headless");
			devMode = runArgs.containsKey("devMode");
			noProfile = runArgs.containsKey("noProfile");
			noUDP = runArgs.containsKey("noUDP");
			if (runArgs.containsKey("tcpPort")) {
				try {
					tcpPort = Integer.parseInt(runArgs.get("tcpPort"));
				}
				catch (NumberFormatException e) {
					Log.error("Invalid TCP Port " + runArgs.get("tcpPort"));
				}
			}
			if (runArgs.containsKey("udpPort")) {
				try {
					udpPort = Integer.parseInt(runArgs.get("udpPort"));
				}
				catch (NumberFormatException e) {
					Log.error("Invalid UDP Port " + runArgs.get("udpPort"));
				}
			}
		}
		if (devMode) {
			Log.set(Log.LEVEL_DEBUG);
		}
	}
	
	@Override
	public void create () {
		Gdx.graphics.setTitle(GameProperties.WINDOW_TITLE);

		GameProfile.load();

		inputMultiplexer = new InputMultiplexer();
		Gdx.input.setInputProcessor(inputMultiplexer);

		engine = new GameEngine(this);
		gameDefinitions = new GameDefinitions();
		gameDefinitions.createDefinitions(false);
		engine.initialize();

		if (!headless) {
			batch = new SpriteBatch();
			viewport = new FitViewport(GameProperties.MAP_WIDTH, GameProperties.MAP_HEIGHT);
			renderer = new GraphicsRenderer(engine, viewport);

			skin = new Skin(Gdx.files.internal("skin/uiskin.json"));
			uiStage = new UIStage(this, new FitViewport(GameProperties.WINDOW_WIDTH, GameProperties.WINDOW_HEIGHT), batch, skin);

			// reconnection
			if (GameProfile.clientSalt != 0) {
				engine.setMode(GameEngine.Mode.Client);
				engine.getSystem(NetDriver.class).setClientReconnect();
			}

			uiStage.initialize(engine);

			if (devMode) {
				devTools = new DevTools(this);
				devTools.loadUI();
			}
		}
		else {
			Log.info("Starting in headless dedicated server mode");
			headlessEngineThread = new Thread(new Runnable() {
				long nextTime = 0;

				@Override
				public void run() {
					engine.netDriver.startServer();
					engine.start();
					nextTime = System.currentTimeMillis() + GameProperties.SIMULATION_UPDATE_INTERVAL_L;
					long curTime;

					while (true) {
						engine.update(GameProperties.SIMULATION_UPDATE_INTERVAL);
						while ((curTime = System.currentTimeMillis()) < nextTime) {
							// waiting
						}
						nextTime = curTime + GameProperties.SIMULATION_UPDATE_INTERVAL_L;
					}

				}
			});
			headlessEngineThread.start();
		}
	}

	@Override
	public void render () {
		if (!headless) {
			engine.update(GameProperties.SIMULATION_UPDATE_INTERVAL);
			renderer.update(GameProperties.SIMULATION_UPDATE_INTERVAL);

			ScreenUtils.clear(0, 0, 0, 1);
			viewport.apply(true);
			renderer.draw(batch);

			uiStage.act(GameProperties.SIMULATION_UPDATE_INTERVAL);
			uiStage.getViewport().apply(true);
			uiStage.draw();

			if (devMode) devTools.render(GameProperties.SIMULATION_UPDATE_INTERVAL);
		}
		else {
			try {
				String input = commandReader.readLine();
				headlessCommand(input);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void headlessCommand(String input) {
		switch (input) {
			case "start":
				synchronized (engine.updateLock) {
					engine.refereeSystem.startMatch();
				}
				break;
			case "restart":
				synchronized (engine.updateLock) {
					engine.refereeSystem.restartMatch();
				}
				break;
			case "restartServer":
				synchronized (engine.updateLock) {
					engine.netDriver.stopServer();
				}
				while (true) {
					synchronized (engine.updateLock) {
						if (!engine.isStopping()) {
							engine.netDriver.startServer();
							engine.start();
							break;
						}
						try {
							Thread.sleep(0);
						} catch (InterruptedException e) {
							Log.error("BoBGame", e.getClass() + " " + e.getMessage());
						}
					}
				}
				break;
			case "":
				Formatter f = new Formatter();
				Log.info(f.format("Bitrate recv=%1.2f send=%1.2f", engine.netDriver.getAverageReceiveBitrate(), engine.netDriver.getAverageSendBitrate()).toString());
				break;
		}
	}

	@Override
	public void resize(int width, int height) {
		Viewport uiViewport = uiStage.getViewport();

		uiViewport.update(width, height);
		int w = uiViewport.getScreenWidth(), h = uiViewport.getScreenHeight();
		viewport.update(w, h);
		viewport.setScreenPosition(uiViewport.getScreenX() + GameProperties.FORCE_METER_WIDTH, uiViewport.getScreenY());
		if (devMode) devTools.resize(width, height);
	}

	@Override
	public void dispose () {
		GameProfile.save();
		if (devMode) devTools.saveSettings();
		batch.dispose();
		uiStage.dispose();
		engine.netDriver.stop();
		if (headlessCommandThread != null) headlessCommandThread.interrupt();
	}

	void onEngineStarted() {
		if (!headless) {
			uiStage.initialize(engine);
		}
	}

	public GameEngine getEngine() {
		return engine;
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

	public static int getTcpPort() {
		return tcpPort;
	}

	public static int getUdpPort() {
		return udpPort;
	}
}
