package com.xam.bobgame.ai.trees;

import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.utils.DistributionAdapters;
import com.badlogic.gdx.ai.utils.random.Distribution;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pools;
import com.badlogic.gdx.utils.XmlReader;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Field;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.ai.trees.tasks.BehaviorSequence;
import com.xam.bobgame.ai.trees.tasks.StepSequence;

public class XmlBehaviorTreeParser<E> {

    protected DistributionAdapters distributionAdapters;
    protected GameStateAdapters gameStateAdapters;
    protected TaskLibrary taskLibrary;

    public XmlBehaviorTreeParser(TaskLibrary taskLibrary) {
        distributionAdapters = new DistributionAdapters();
        this.taskLibrary = taskLibrary;
        gameStateAdapters = taskLibrary.getGameStateAdapters();
        distributionAdapters = taskLibrary.getDistributionAdapters();
    }

    @SuppressWarnings("unchecked")
    public BehaviorTree<E> parse(XmlReader.Element xml, boolean sequence) {
        Task<E> root;
        BehaviorTree<E> tree;
        if (sequence) {
//            root = new StepSequence<>();
//            tree = new BehaviorSequence<>(root);
            root = Pools.obtain(StepSequence.class);
            tree = Pools.obtain(BehaviorSequence.class);
//            tree.resetTask();
            tree.addChild(root);
            root.setControl(tree);
            openChildren(xml, root);
        }
        else {
//            tree = new PoolableBehaviorTree<>(root);
            tree = Pools.obtain(PoolableBehaviorTree.class);
            root = open(xml, tree);
//            tree.resetTask();
            tree.addChild(root);
        }
        return tree;
    }

    protected static <E> void printTree (Task<E> task, int indent) {
        for (int i = 0; i < indent; i++)
            System.out.print(' ');
        if (task.getGuard() != null) {
            System.out.println("Guard");
            indent = indent + 2;
            printTree(task.getGuard(), indent);
            for (int i = 0; i < indent; i++)
                System.out.print(' ');
        }
        System.out.println(task.getClass().getSimpleName());
        for (int i = 0; i < task.getChildCount(); i++) {
            printTree(task.getChild(i), indent + 2);
        }
    }

    protected Task<E> open(XmlReader.Element element, Task<E> parent) {
        String elementName = element.getName();
        Task<E> task = null;

        try {
            if (elementName.endsWith(":guard") || elementName.endsWith("guard")) {
                Task<E> guardTask = null;
                for (int i = 0; i < element.getChildCount(); ++i) {
                    XmlReader.Element child = element.getChild(i);
                    task = createTask(child);
                    task.setGuard(guardTask);
                    if (child.getChildCount() > 0 || i == element.getChildCount()-1) {
                        element = child;
                        break;
                    }
                    else {
                        guardTask = task;
                    }
                }
            }
            else {
                task = createTask(element);
            }

            if (task == null) throw new GdxRuntimeException("Couldn't create task at " + elementName);
            if (parent != null) {
                parent.addChild(task);
                task.setControl(parent);
            }
        }
        catch (Throwable t) {
            throw new GdxRuntimeException("Failed parsing task " + elementName, t);
        }

        openChildren(element, task);

        return task;
    }

    protected void openChildren(XmlReader.Element element, Task<E> task) {
        for (int i = 0; i < element.getChildCount(); ++i) {
            open(element.getChild(i), task);
        }
    }

    @SuppressWarnings("unchecked")
    protected Task<E> createTask(XmlReader.Element element) {
        String[] name = element.getName().split(":");
        ObjectMap<String,String> attributes = element.getAttributes();
        Task<E> task;
        if (name.length == 1) {
            task = (Task<E>) taskLibrary.createTask(name[0]);
            if (attributes != null) {
                setAttributes(task, name[0], attributes);
            }
        }
        else {
            task = (Task<E>) taskLibrary.createTask(name[0], name[1]);
            if (attributes != null) {
                setAttributes(task, name[0], name[1], attributes);
            }
        }

        if (task == null) {
            throw new GdxRuntimeException("Unknown task at:\n" + element);
        }

        return task;
    }

    protected void setAttributes(Task<E> task, String taskName, ObjectMap<String, String> attributes) {
        setAttributes(task, "", taskName, attributes);
    }

    protected void setAttributes(Task<E> task, String namespace, String taskName, ObjectMap<String, String> attributes) {
        if (attributes == null) return;
        for (ObjectMap.Entry<String,String> attribute : attributes.entries()) {
            Field field = null;
            Object value = getValue(attribute.value);
            if (value == null) {
                Log.debug("XmlBehaviorTreeParser", "Warning: value parsed to null: " + attribute.value);
            }
            try {
                field = ClassReflection.getField(task.getClass(), attribute.key);
            }
            catch (ReflectionException ignored) {

            }
            catch (NullPointerException e) {
                throw new GdxRuntimeException("Null attribute " + taskName + " " + attribute.key, e);
            }
            if (field != null) {
                try {
                    field.set(task, castValue(field, value));
                } catch (ReflectionException e) {
                    e.printStackTrace();
                }
            }
            if (field == null) taskLibrary.setAttribute(task, namespace, taskName, attribute.key, value);
        }
    }

    public static Object getValue(String string) {
        if (string == null) return null;

        if (string.equals("true")) {
            return Boolean.TRUE;
        }
        else if (string.equals("false")) {
            return Boolean.FALSE;
        }

        try {
            return Integer.parseInt(string);
        }
        catch (NumberFormatException ignored) {}
        try {
            return Float.parseFloat(string);
        }
        catch (NumberFormatException ignored) {}

        return string;
    }

    // from BehaviorTreeParser
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Object castValue (Field field, Object value) {
        Class<?> type = field.getType();
        Object ret = null;

        if (ClassReflection.isAssignableFrom(GameState.class, type)) {
            Class<GameState> gameStateType = (Class<GameState>)type;
            if (value instanceof Number) {
                Number numberValue = (Number) value;
                return gameStateAdapters.toGameState("constant," + numberValue, gameStateType);
            }
            else if (value instanceof String) {
                String stringValue = (String) value;
                return gameStateAdapters.toGameState(stringValue, gameStateType);
            }
            return null;
        }

        if (value instanceof Number) {
            Number numberValue = (Number)value;
            if (type == int.class || type == Integer.class)
                ret = numberValue.intValue();
            else if (type == float.class || type == Float.class)
                ret = numberValue.floatValue();
            else if (type == long.class || type == Long.class)
                ret = numberValue.longValue();
            else if (type == double.class || type == Double.class)
                ret = numberValue.doubleValue();
            else if (type == short.class || type == Short.class)
                ret = numberValue.shortValue();
            else if (type == byte.class || type == Byte.class)
                ret = numberValue.byteValue();
            else if (ClassReflection.isAssignableFrom(Distribution.class, type)) {
                @SuppressWarnings("unchecked")
                Class<Distribution> distributionType = (Class<Distribution>)type;
                ret = distributionAdapters.toDistribution("constant," + numberValue, distributionType);
            }
        } else if (value instanceof Boolean) {
            if (type == boolean.class || type == Boolean.class) {
                ret = value;
            }
            else if (type == String.class) {
                ret = value.toString();
            }
        } else if (value instanceof String) {
            String stringValue = (String)value;
            if (type == String.class)
                ret = value;
            else if (type == char.class || type == Character.class) {
                if (stringValue.length() != 1) throw new GdxRuntimeException("Invalid character '" + value + "'");
                ret = Character.valueOf(stringValue.charAt(0));
            } else if (ClassReflection.isAssignableFrom(Distribution.class, type)) {
                @SuppressWarnings("unchecked")
                Class<Distribution> distributionType = (Class<Distribution>)type;
                ret = distributionAdapters.toDistribution(stringValue, distributionType);
            } else if (ClassReflection.isAssignableFrom(Enum.class, type)) {
                Enum<?>[] constants = (Enum<?>[])type.getEnumConstants();
                for (int i = 0, n = constants.length; i < n; i++) {
                    Enum<?> e = constants[i];
                    if (e.name().equalsIgnoreCase(stringValue)) {
                        ret = e;
                        break;
                    }
                }
            }
        }
        return ret;
    }

    private static boolean containsFloatingPointCharacters (String value) {
        for (int i = 0, n = value.length(); i < n; i++) {
            switch (value.charAt(i)) {
                case '.':
                case 'E':
                case 'e':
                    return true;
            }
        }
        return false;
    }
}
