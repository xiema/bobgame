package com.xam.bobgame.dev.tools;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Field;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.dev.DevTools;
import com.xam.bobgame.dev.utils.DevToolWindow;
import com.xam.bobgame.entity.ComponentMappers;


/**
 * Sample usage of extending DevToolWindow for displaying simple game info
 */
public class EntityInspector extends DevToolWindow {
    /** UI elements that will change
     */
    public Label entityIdLabel;
    public Label posXLabel;
    public Label posYLabel;
    public Label velXLabel;
    public Label velYLabel;

    public DevTools devTools;
    public Entity focusedEntity;


    public EntityInspector(DevTools devTools, Skin skin) {
        super("EntityInspector", skin);

        this.devTools = devTools;

        defaults().left().expandX().fillX();
        columnDefaults(0).minWidth(140f);
        columnDefaults(1).minWidth(200f);

        // GENERAL INFO
        addLabelCell("Entity ID", "small");
        entityIdLabel = addLabelCell("", "small");
        row();

        devTools.devUIStage.addListener(new ClickListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (button == 1) { // right click
                    updateFocus();
                }
                return false;
            }
        });

        addLabelCell("PosX", "small");
        posXLabel = addLabelCell("", "small");
        row();
        addLabelCell("PosY", "small");
        posYLabel = addLabelCell("", "small");
        row();
        addLabelCell("VelX", "small");
        velXLabel = addLabelCell("", "small");
        row();
        addLabelCell("VelY", "small");
        velYLabel = addLabelCell("", "small");

        // load saved window settings, or use specified defaults
        loadWindowSettings(devTools.getWindowSettingsXML(), 0f, 0f, getMinWidth(), getMinHeight(), true);
    }

    /** Main update function
     */
    @Override
    public void act (float delta) {
        super.act(delta);
        if (focusedEntity != devTools.devToolsSystem.focusedEntity) {
            focusedEntity = devTools.devToolsSystem.focusedEntity;
            updateFocus();
        }
        updateInfo();
    }

    public void updateFocus() {
        if (focusedEntity != null) {
            entityIdLabel.setText(ComponentMappers.identity.get(focusedEntity).id);
        }
    }

    public void updateInfo() {
        if (focusedEntity == null) {
            entityIdLabel.setColor(Color.RED);
            return;
        }
        entityIdLabel.setColor(Color.WHITE);
        PhysicsBodyComponent pb = ComponentMappers.physicsBody.get(focusedEntity);
        Body body = pb.body;
        Vector2 pos = body.getPosition();
        Vector2 vel = body.getLinearVelocity();
        posXLabel.setText(String.valueOf(pos.x));
        posYLabel.setText(String.valueOf(pos.y));
        velXLabel.setText(String.valueOf(vel.x));
        velYLabel.setText(String.valueOf(vel.y));
    }

    public Field[] getDeclaredFields (Class<? extends Component> c) {
        return ClassReflection.getDeclaredFields(c);
    }
}
