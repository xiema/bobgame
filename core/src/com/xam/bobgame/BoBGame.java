package com.xam.bobgame;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.game.PhysicsSystem;
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

	Stage uiStage;
	Viewport uiViewport;
	Label bitrateLabel;

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
		viewport = new FitViewport(10, 10);
		stage = new Stage(viewport, batch);
		engine = new GameEngine();
		renderer = new GraphicsRenderer(engine, stage);
		netDriver = new NetDriver();
		engine.initialize();

		Skin skin = new Skin(Gdx.files.internal("skin/uiskin.json"));
		uiViewport = new FitViewport(500, 500);
		uiStage = new Stage(uiViewport, batch);
		bitrateLabel = new Label("0", skin);
		bitrateLabel.setName("bitrateLabel");
		uiStage.addActor(bitrateLabel);
		bitrateLabel.setPosition(0, 0, Align.bottomLeft);

		if (mode == 1) {
			netDriver.setMode(NetDriver.Mode.Server);
			netDriver.startServer();
		}

		engine.gameSetup();
		if (mode != 2) {
			engine.getSystem(GameDirector.class).getPlayerEntity().getComponent(PhysicsBodyComponent.class).body.applyForceToCenter(MathUtils.random() * 1000f, MathUtils.random() * 100f, true);
			engine.getSystem(PhysicsSystem.class).setEnabled(true);
		}
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
				netDriver.connect("127.0.0.1");
			}
		}
		engine.update(deltaTime);
//		if (mode != 2) engine.userInput(viewport);
		if (mode == 1) {
			netDriver.syncClients(engine);
			if (netDriver.getServer().getConnections().length > 0) bitrateLabel.setText(String.valueOf(netDriver.updateBitrate(deltaTime)));
		}

		stage.act(deltaTime);
		ScreenUtils.clear(0, 0, 0, 1);
		viewport.apply(true);
		renderer.draw(batch);

		uiStage.act(deltaTime);
		uiViewport.apply(true);
		uiStage.draw();

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
