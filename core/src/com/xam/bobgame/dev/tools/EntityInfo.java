package com.xam.bobgame.dev.tools;

import com.badlogic.ashley.core.Component;
import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Field;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import com.xam.bobgame.components.GraphicsComponent;
import com.xam.bobgame.components.PhysicsBodyComponent;
import com.xam.bobgame.dev.DevTools;
import com.xam.bobgame.dev.utils.DevToolWindow;
import com.xam.bobgame.entity.ComponentMappers;


/**
 * Tool that dynamically detects all Components attached to the focused Entity and presents a basic, simply-formatted
 * display of all fields and values of each Component. Useful for inspecting Components or Component values that are not
 * programmed into {@link EntityInspector}.
 */
public class EntityInfo extends DevToolWindow {
    /** UI elements that will change
     */
    public Label entityIdLabel;
    public SelectBox<String> componentSelectBox;

    public DevTools devTools;
    public Entity focusedEntity;
    public ObjectMap<Class<?>, Boolean> focusedEntityComponents = new ObjectMap<>();
    public Array<Label> labelNames = new Array<>();
    public Array<Label> labelValues = new Array<>();

    public ObjectMap<String, Class<? extends Component>> stringToComponentClass = new ObjectMap<>();
    public String selectedComponentName;

    public EntityInfo(DevTools devTools, Skin skin) {
        super("EntityInfo", skin);

        this.devTools = devTools;

        defaults().left().expandX().fillX();
        columnDefaults(0).minWidth(140f);
        columnDefaults(1).minWidth(200f);

        addLabelCell("Entity ID", "small");
        entityIdLabel = addLabelCell("", "small");
        row();

        add(componentSelectBox = new SelectBox<>(skin)).colspan(2);
        componentSelectBox.setMaxListCount(5);
        row();

        // load saved window settings, or use specified defaults
        loadWindowSettings(devTools.getWindowSettingsXML(), 0f, 0f, getMinWidth(), getMinHeight(), true);

        focusedEntityComponents.put(PhysicsBodyComponent.class, false);
        focusedEntityComponents.put(GraphicsComponent.class, false);
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

    private Array<String> availableComponents = new Array<>();
    public void updateFocus() {
        if (focusedEntity != null) {
            entityIdLabel.setText(ComponentMappers.identity.get(focusedEntity).id);
            availableComponents.clear();
            focusedEntityComponents.clear();
            for (Component component : focusedEntity.getComponents()) {
                Class<? extends Component> clazz = component.getClass();
                if (focusedEntity.getComponent(clazz) != null) {
                    focusedEntityComponents.put(clazz, true);
                    availableComponents.add(clazz.getSimpleName());
                }
                else {
                    focusedEntityComponents.put(clazz, false);
                }
                if (!stringToComponentClass.containsKey(clazz.getSimpleName())) {
                    stringToComponentClass.put(clazz.getSimpleName(), clazz);
                }
            }
            componentSelectBox.setItems(availableComponents);
        }
    }

    public void updateInfo() {
        if (focusedEntity == null) {
            entityIdLabel.setColor(Color.RED);
            return;
        }

        if (componentSelectBox.getSelectedIndex() == -1) return;
        String componentName = componentSelectBox.getSelected();
        Class<? extends Component> componentClass = stringToComponentClass.get(componentName);
        Field[] fields = getDeclaredFields(componentClass);
        ensureCellCapacity(fields.length);
        Component component = focusedEntity.getComponent(componentClass);

        if (component == null) {
            entityIdLabel.setColor(Color.RED);
            return;
        }

        entityIdLabel.setColor(Color.WHITE);

        int i;
        for (i = 0; i < fields.length; ++i) {
            Label labelName = labelNames.get(i);
            Label labelValue = labelValues.get(i);
            labelName.setText(fields[i].getName());
            try {
                Object value = fields[i].get(component);
                if (value == null) {
                    labelValue.setText("NULL");
                }
                else {
                    labelValue.setText(value.toString());
                }
            } catch (ReflectionException e) {
                e.printStackTrace();
                labelValue.setText("ReflectionException");
            } catch (NullPointerException e) {
                e.printStackTrace();
                labelValue.setText("NullPointerException");
            }
        }

        if (i < labelNames.size) {
            for (; i < labelNames.size; ++i) {
                removeActor(labelNames.pop());
                removeActor(labelValues.pop());
            }
        }

        if (!componentName.equals(selectedComponentName)) {
            setHeight(getPrefHeight());
            selectedComponentName = componentName;
        }
    }

    public void ensureCellCapacity(int capacity) {
        while (labelNames.size < capacity) {
            Label label = addLabelCell("Unlabeled", "small");
            label.setEllipsis(true);
            labelNames.add(label);
            label = addLabelCell("None", "small");
            label.setEllipsis(true);
            labelValues.add(label);
            row();
        }
    }

    public Field[] getDeclaredFields (Class<?> c) {
        if (!ClassReflection.isAssignableFrom(Component.class, c)) {
            error("Class " + c.getSimpleName() + " is not a Component or GameDefinition");
            return null;
        }
        return ClassReflection.getDeclaredFields(c);
    }
}
