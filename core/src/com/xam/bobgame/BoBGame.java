package com.xam.bobgame;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.xam.bobgame.graphics.GraphicsRenderer;
import com.xam.bobgame.net.NetDriver;

import java.util.Map;

public class BoBGame extends ApplicationAdapter {
	int mode = 0;

	SpriteBatch batch;
	GameEngine engine;
	GraphicsRenderer renderer;
	Viewport viewport;
	NetDriver netDriver;
	Stage stage;

	private static long counter = 0;

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
		}
	}
	
	@Override
	public void create () {
		batch = new SpriteBatch();
		viewport = new FitViewport(500, 500);
		stage = new Stage(viewport, batch);
		engine = new GameEngine();
		renderer = new GraphicsRenderer(engine, stage);
		netDriver = new NetDriver();
		engine.initialize();

		if (mode == 1) {
			netDriver.setMode(NetDriver.Mode.Server);
			netDriver.startServer();
		}

		engine.gameSetup();
	}

	int t = 0;

	@Override
	public void render () {
		float deltaTime = Gdx.graphics.getDeltaTime();

		if (mode == 2) {
			if (netDriver.isConnected()) {
				netDriver.syncWithServer(engine);
			}
			else if (t++ % 100 == 0) {
				netDriver.connect("0.0.0.0");
			}
		}
		engine.update(deltaTime);
		if (mode != 2) engine.userInput(viewport);
		if (mode == 1) netDriver.syncClients(engine);

		stage.act(deltaTime);
		ScreenUtils.clear(0, 0, 0, 1);
		stage.draw();

//		viewport.apply(true);
//		ScreenUtils.clear(0, 0, 0, 1);
//		batch.setProjectionMatrix(viewport.getCamera().combined);
//		batch.begin();
//		renderer.draw(batch);
//		batch.end();

		counter++;
	}

	@Override
	public void resize(int width, int height) {
		viewport.update(width, height);
	}

	@Override
	public void dispose () {
		batch.dispose();
		netDriver.stop();
	}

	public static long getCounter() {
		return counter;
	}
}
