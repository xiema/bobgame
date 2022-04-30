package com.xam.bobgame;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.game.PhysicsSystem;
import com.xam.bobgame.graphics.GraphicsRenderer;
import com.xam.bobgame.net.NetDriver;
import com.xam.bobgame.ui.UIStage;

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

		InputMultiplexer input = new InputMultiplexer();
		engine.addInputProcessor(input, viewport);
		Gdx.input.setInputProcessor(input);
		engine.initialize();
		netDriver = engine.getSystem(NetDriver.class);

		Skin skin = new Skin(Gdx.files.internal("skin/uiskin.json"));
		uiViewport = new FitViewport(800, 500);
		uiStage = new UIStage(this, uiViewport, batch, skin);

		if (mode == 1) {
			netDriver.setMode(NetDriver.Mode.Server);
			netDriver.getServer().start(NetDriver.PORT_TCP, NetDriver.PORT_UDP);
			engine.gameSetup();
			engine.getSystem(GameDirector.class).getPlayerEntity().getComponent(PhysicsBodyComponent.class).body.applyForceToCenter(MathUtils.random() * 1000f, MathUtils.random() * 100f, true);
		}

		engine.getSystem(PhysicsSystem.class).setEnabled(true);
	}

	@Override
	public void render () {
		float deltaTime = Gdx.graphics.getDeltaTime();

		engine.update(deltaTime);

		stage.act(deltaTime);
		ScreenUtils.clear(0, 0, 0, 1);
		viewport.apply(true);
		renderer.draw(batch);

		uiStage.act(deltaTime);
		uiViewport.apply(true);
		uiStage.draw();

		counter++;
	}

	@Override
	public void resize(int width, int height) {
		viewport.update(width, height);
	}

	@Override
	public void dispose () {
		batch.dispose();
		netDriver.getClient().stop();
		netDriver.getServer().stop();
	}

	public static long getCounter() {
		return counter;
	}

	public GameEngine getEngine() {
		return engine;
	}
}
