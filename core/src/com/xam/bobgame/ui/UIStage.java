
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
import com.xam.bobgame.events.classes.ScoreBoardRefreshEvent;
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

    final Label playerScoreLabel;
    final Label matchTimeLabel;
    final Label winLabel;

    final MainMenu mainMenu;

    final ScoreBoard scoreBoard;
    final SpectatorList spectatorList;
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
        menuTable.setBackground(skin.getDrawable("light-blue"));

        scoreBoard = new ScoreBoard(skin);
//        scoreBoard.setWidth(GameProperties.MENU_WIDTH);
        menuTable.add(scoreBoard).fill().expand().pad(5, 5, 0, 5);
        menuTable.row();

        spectatorList = new SpectatorList(skin);
        menuTable.add(spectatorList).fillX().expandX().pad(5, 5, 5, 5).minHeight(spectatorList.getMinHeight());
        menuTable.row();

        mainMenu = new MainMenu(skin);
//        mainMenu.setPosition(GameProperties.WINDOW_WIDTH, 0, Align.bottomRight);
        menuTable.add(mainMenu).fillX().expandX();

        Label label = new Label("Score:", skin);
        label.setPosition(GameProperties.FORCE_METER_WIDTH + GameProperties.WINDOW_HEIGHT - 85f, 12f, Align.bottomRight);
        addActor(label);
        playerScoreLabel = new Label("0", skin);
        playerScoreLabel.setAlignment(Align.right);
        playerScoreLabel.setPosition(GameProperties.FORCE_METER_WIDTH + GameProperties.WINDOW_HEIGHT - 15f, 12f, Align.bottomRight);
        addActor(playerScoreLabel);

        matchTimeLabel = new Label("0", skin);
        matchTimeLabel.setFontScale(2);
        matchTimeLabel.setAlignment(Align.center);
        matchTimeLabel.setPosition(0.5f * GameProperties.WINDOW_HEIGHT + GameProperties.FORCE_METER_WIDTH, GameProperties.WINDOW_HEIGHT - 25f, Align.top);
        addActor(matchTimeLabel);
        winLabel = new Label("", skin);
        winLabel.setFontScale(4);
        winLabel.setAlignment(Align.center);
        winLabel.setPosition(0.5f * GameProperties.WINDOW_HEIGHT + GameProperties.FORCE_METER_WIDTH, 0.5f * GameProperties.WINDOW_HEIGHT, Align.center);

        addActor(menuTable);

        ((InputMultiplexer) Gdx.input.getInputProcessor()).addProcessor(this);

        listeners.put(MatchRestartEvent.class, new EventListenerAdapter<MatchRestartEvent>() {
            @Override
            public void handleEvent(MatchRestartEvent event) {
                winLabel.remove();
                playerScoreLabel.setText(0);
            }
        });
        listeners.put(MatchEndedEvent.class, new EventListenerAdapter<MatchEndedEvent>() {
            @Override
            public void handleEvent(MatchEndedEvent event) {
                RefereeSystem refereeSystem = engine.getSystem(RefereeSystem.class);
                if (!refereeSystem.getPlayerInfo(refereeSystem.getLocalPlayerId()).inPlay) return;

                if (refereeSystem.getLocalPlayerId() == event.winningPlayerId) {
                    winLabel.setText("YOU WIN!");
                }
                else {
                    winLabel.setText("YOU LOSE!");
                }
                addActor(winLabel);
            }
        });
        listeners.put(ScoreBoardRefreshEvent.class, new EventListenerAdapter<ScoreBoardRefreshEvent>() {
            @Override
            public void handleEvent(ScoreBoardRefreshEvent event) {
                if (!refereeSystem.isLocalPlayerJoined()) return;
                playerScoreLabel.setText(refereeSystem.getPlayerInfo(refereeSystem.getLocalPlayerId()).score);
            }
        });
    }

    public void initialize(GameEngine engine) {
        engine.getSystem(EventsSystem.class).addListeners(listeners);
        forceMeter.initialize(engine);
        mainMenu.initialize(engine);
        scoreBoard.initialize(engine);
        spectatorList.initialize(engine);
        winLabel.remove();
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
