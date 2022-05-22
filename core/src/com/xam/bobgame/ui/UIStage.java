
package com.xam.bobgame.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.xam.bobgame.BoBGame;
import com.xam.bobgame.events.classes.MatchEndedEvent;
import com.xam.bobgame.events.classes.MatchRestartEvent;
import com.xam.bobgame.game.RefereeSystem;
import com.xam.bobgame.GameEngine;
import com.xam.bobgame.GameProperties;
import com.xam.bobgame.events.*;
import com.xam.bobgame.net.NetDriver;

import java.util.Formatter;

public class UIStage extends Stage {

    final BoBGame game;
    final GameEngine engine;
    final RefereeSystem refereeSystem;
    final NetDriver netDriver;
    final Skin skin;

    final Label sendBitrateLabel;
    final Label receiveBitrateLabel;
    final Label matchTimeLabel;
    final Label winLabel;

    final MainMenu mainMenu;

    final ScoreBoard scoreBoard;
    final ForceMeter forceMeter;

    private final ObjectMap<Class<? extends GameEvent>, GameEventListener> listeners = new ObjectMap<>();

    public UIStage(final BoBGame game, Viewport viewport, Batch batch, Skin skin) {
        super(viewport, batch);
        this.game = game;
        this.skin = skin;
        engine = game.getEngine();
        netDriver = engine.getSystem(NetDriver.class);
        refereeSystem = engine.getSystem(RefereeSystem.class);

        forceMeter = new ForceMeter(skin);
        forceMeter.setPosition(0, 0, Align.bottomLeft);
        addActor(forceMeter);

        Table menuTable = new Table();
        menuTable.setSize(GameProperties.MENU_WIDTH, GameProperties.WINDOW_HEIGHT);
        menuTable.setPosition(GameProperties.WINDOW_WIDTH, 0, Align.bottomRight);

        scoreBoard = new ScoreBoard(skin);
//        scoreBoard.setWidth(GameProperties.MENU_WIDTH);
        menuTable.add(scoreBoard).colspan(2).fill().expand();
        menuTable.row();

        mainMenu = new MainMenu(skin);
//        mainMenu.setPosition(GameProperties.WINDOW_WIDTH, 0, Align.bottomRight);
        menuTable.add(mainMenu).align(Align.left).width(GameProperties.MENU_WIDTH * 0.5f);

        matchTimeLabel = new Label("0", skin);
        matchTimeLabel.setAlignment(Align.center);
        matchTimeLabel.setPosition(0.5f * GameProperties.WINDOW_HEIGHT + GameProperties.FORCE_METER_WIDTH, GameProperties.WINDOW_HEIGHT - 15f, Align.top);
        addActor(matchTimeLabel);
        winLabel = new Label("", skin);
        winLabel.setAlignment(Align.center);
        winLabel.setPosition(0.5f * GameProperties.WINDOW_HEIGHT + GameProperties.FORCE_METER_WIDTH, 0.5f * GameProperties.WINDOW_HEIGHT, Align.center);

        Table subTable = new Table();
        menuTable.add(subTable).width(GameProperties.MENU_WIDTH * 0.5f);

        subTable.defaults().expand().fill();
        sendBitrateLabel = new Label("0", skin) {
            @Override
            public void act(float delta) {
                super.act(delta);
                Formatter formatter = new Formatter();
                setText(formatter.format("%1.2f", netDriver.getAverageSendBitrate()).toString());
            }
        };
        sendBitrateLabel.setAlignment(Align.right);
        sendBitrateLabel.setPosition(GameProperties.WINDOW_WIDTH, 0, Align.bottomRight);
        receiveBitrateLabel = new Label("0", skin) {
            @Override
            public void act(float delta) {
                super.act(delta);
                Formatter formatter = new Formatter();
                setText(formatter.format("%1.2f", netDriver.getAverageReceiveBitrate()).toString());
            }
        };
        receiveBitrateLabel.setAlignment(Align.right);
        receiveBitrateLabel.setPosition(GameProperties.WINDOW_WIDTH, 0, Align.bottomRight);

        Label label;
        subTable.add(label = new Label("Recv", skin));
        subTable.add(receiveBitrateLabel).align(Align.right);
        label.setAlignment(Align.right);
        subTable.row();
        subTable.add(label = new Label("Send", skin));
        label.setAlignment(Align.right);
        subTable.add(sendBitrateLabel).align(Align.right);

        addActor(menuTable);

        ((InputMultiplexer) Gdx.input.getInputProcessor()).addProcessor(this);

        listeners.put(MatchRestartEvent.class, new EventListenerAdapter<MatchRestartEvent>() {
            @Override
            public void handleEvent(MatchRestartEvent event) {
                winLabel.remove();
            }
        });
        listeners.put(MatchEndedEvent.class, new EventListenerAdapter<MatchEndedEvent>() {
            @Override
            public void handleEvent(MatchEndedEvent event) {
                if (engine.getSystem(RefereeSystem.class).getLocalPlayerId() == event.winningPlayerId) {
                    winLabel.setText("YOU WIN!");
                }
                else {
                    winLabel.setText("YOU LOSE!");
                }
                addActor(winLabel);
            }
        });
    }

    public void initialize(GameEngine engine) {
        engine.getSystem(EventsSystem.class).addListeners(listeners);
        forceMeter.initialize(engine);
        mainMenu.initialize(engine);
        scoreBoard.initialize(engine);
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        switch (refereeSystem.getMatchState()) {
            case NotStarted:
                matchTimeLabel.setText("");
                break;
            case Started:
                matchTimeLabel.setText((int) refereeSystem.getMatchTimeRemaining());
                break;
            case Ended:
                matchTimeLabel.setText("Game Over");
                break;
        }
    }
}
