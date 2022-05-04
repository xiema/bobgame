package com.xam.bobgame.ai.trees;

import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.TaskCloner;
import com.badlogic.gdx.ai.btree.annotation.TaskAttribute;
import com.badlogic.gdx.ai.btree.branch.*;
import com.badlogic.gdx.ai.btree.decorator.*;
import com.badlogic.gdx.ai.btree.leaf.Failure;
import com.badlogic.gdx.ai.btree.leaf.Success;
import com.badlogic.gdx.ai.btree.leaf.Wait;
import com.badlogic.gdx.ai.btree.utils.DistributionAdapters;
import com.badlogic.gdx.ai.utils.random.Distribution;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.reflect.Annotation;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Field;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import com.xam.bobgame.ai.trees.tasks.*;
import com.xam.bobgame.ai.trees.tasks.Timer;

@SuppressWarnings("rawtypes")
public class TaskLibrary {

    private ObjectMap<String, ObjectMap<String, TaskAdapter>> namespaces = new ObjectMap<>();
    private ObjectMap<Class<? extends Task>, Pool<? extends Task>> taskPoolMap = new ObjectMap<>();

    private GameStateAdapters gameStateAdapters;
    private DistributionAdapters distributionAdapters;

    private CustomTaskParser customTaskParser;

    public TaskLibrary(DistributionAdapters distributionAdapters, GameStateAdapters gameStateAdapters) {
        Task.TASK_CLONER = taskCloner;
        this.gameStateAdapters = gameStateAdapters;
        this.distributionAdapters = distributionAdapters;
        customTaskParser = new CustomTaskParser(this);

        Class<?>[] classes = new Class<?>[] {// @off - disable libgdx formatter
                AlwaysFail.class,
                AlwaysSucceed.class,
                DynamicGuardSelector.class,
                Failure.class,
                Include.class,
                Invert.class,
                Parallel.class,
                Random.class,
                RandomSelector.class,
                RandomSequence.class,
                Repeat.class,
                Selector.class,
                SemaphoreGuard.class,
                Sequence.class,
                Success.class,
                UntilFail.class,
                UntilSuccess.class,
                Wait.class,
                WaitWhile.class,

                CheckFloat.class,

                Loop.class,
                WaitSuccess.class,
                StepSequence.class,
                Print.class,
                Timer.class,
                GuardStepSequence.class,
        }; // @on - enable libgdx formatter
        for (Class<?> c : classes) {
            String cn = c.getSimpleName();
            String alias = Character.toLowerCase(cn.charAt(0)) + (cn.length() > 1 ? cn.substring(1) : "");
            //noinspection unchecked
            addTask(alias, (Class<? extends Task>) c);
        }
    }

    public void addTask(String keyword, Class<? extends Task> type) {
        addTask("", keyword, type);
    }

    public void addTask(String namespace, String keyword, Class<? extends Task> type) {
        TaskAdapter taskAdapter = new TaskAdapter(type);
        addTaskAdapter(namespace, keyword, taskAdapter);
    }

    public void addCustomTasks(String namespace, XmlReader.Element xml, boolean schema) {
        if (schema) {
            for (XmlReader.Element group : xml.getChildrenByName("xs:group")) {
                if (group.getAttribute("name").equals("Templates")) {
                    XmlReader.Element choice = group.getChildByName("xs:choice");
                    for (int i = 0, n = choice.getChildCount(); i < n; ++i) {
                        XmlReader.Element element = choice.getChild(i);
                        String id = element.getAttribute("name");
                        XmlReader.Element template = element.getChildByNameRecursive("Template").getChild(0);
                        ObjectMap<String,String> defaults = new ObjectMap<>();
                        for (XmlReader.Element attribute : element.getChildByName("xs:complexType").getChildrenByNameRecursively("xs:attribute")) {
                            defaults.put(attribute.getAttribute("name"), attribute.getAttribute("default", null));
                        }
                        CustomTaskAdapter adapter = customTaskParser.parse(namespace, template, defaults);
                        addTaskAdapter(namespace, id, adapter);
                    }
                }
            }
        }
        else {
            for (int i = 0, n = xml.getChildCount(); i < n; ++i) {
                XmlReader.Element element = xml.getChild(i);
                String keyword = element.get("id");
                addTaskAdapter(namespace, keyword, customTaskParser.parse(namespace, element));
            }
        }
    }

    public void addTaskAdapter(String keyword, TaskAdapter taskAdapter) {
        addTaskAdapter("", keyword, taskAdapter);
    }

    public void addTaskAdapter(String namespace, String keyword, TaskAdapter taskAdapter) {
        ObjectMap<String, TaskAdapter> adapterMap = namespaces.get(namespace, namespaces.get(""));
        if (adapterMap == null) {
            adapterMap = new ObjectMap<>();
            namespaces.put(namespace, adapterMap);
        }
        adapterMap.put(keyword, taskAdapter);
        if (!taskPoolMap.containsKey(taskAdapter.type)) {
            taskPoolMap.put(taskAdapter.type, taskAdapter.pool);
        }
    }

    public TaskAdapter getTaskAdapter(String keyword) {
        return getTaskAdapter("", keyword);
    }

    public TaskAdapter getTaskAdapter(String namespace, String keyword) {
        ObjectMap<String, TaskAdapter> adapterMap = namespaces.get(namespace, namespaces.get(""));
        if (adapterMap != null) {
            return adapterMap.get(keyword);
        }
        return null;
    }

    public Task createTask(String keyword) {
        return createTask("", keyword);
    }

    public Task createTask(String namespace, String keyword) {
        TaskAdapter taskAdapter = getTaskAdapter(keyword);
        if (taskAdapter != null) return taskAdapter.createTask();
        return null;
    }
    public boolean setAttribute(Task task, String keyword, String attributeName, Object value) {
        return setAttribute(task, "", keyword, attributeName, value);
    }

    public boolean setAttribute(Task task, String namespace, String keyword, String attributeName, Object value) {
        TaskAdapter taskAdapter = getTaskAdapter(namespace, keyword);
        if (taskAdapter != null) {
            Class<?> attributeType = taskAdapter.getAttributeType(attributeName);
            if (attributeType != null) {
                //noinspection
                return taskAdapter.setAttribute(task, attributeName, castValue(attributeType, value));
            }
        }
        return false;
    }

    public static class TaskAdapter {
        Pool<? extends Task> pool;
        public final Class<? extends Task> type;
        public final String classPath;

        ObjectMap<String, AttributeResolver> resolverMap;

        public TaskAdapter(Class<? extends Task> type) {
            this.type = type;
            pool = Pools.get(type);
            this.classPath = type.getName();
            resolverMap = new ObjectMap<>();
            initialize();
        }

        // public
        public Task createTask() {
            Task task = null;
//            try {
                //noinspection unchecked
//                task = (Task) ClassReflection.newInstance(ClassReflection.forName(classPath));
//            } catch (ReflectionException e) {
//                e.printStackTrace();
//            }
            task = pool.obtain();
            return task;
        }

        // Overridable
        protected void initialize() {

        }
        // Access in Override
        protected void addAttributeResolver(String key, AttributeResolver resolver) {
            resolverMap.put(key, resolver);
        }

        // Internal to Task Library
        Class<?> getAttributeType(String name) {
            AttributeResolver resolver = resolverMap.get(name);
            return resolver == null ? null : resolver.type;
        }

        boolean setAttribute(Task task, String name, Object value) {
            return resolverMap.get(name).set(task, value);
        }
    }

    public static class AttributeResolver {
        protected String name;
        protected Class<?> type;
        protected Object defaultValue;

        public AttributeResolver(String name, Class<?> type) {
            this(name, type, null);
        }

        public AttributeResolver(String name, Class<?> type, Object defaultValue) {
            this.name = name;
            this.type = type;
            this.defaultValue = defaultValue;
        }

        protected boolean set(Task task, Object value) {
            //TODO: store field on instantiation
            try {
                Field field = ClassReflection.getField(task.getClass(), name);
                field.set(task, value == null ? defaultValue : value);
                return true;
            } catch (ReflectionException e) {
                e.printStackTrace();
            }
            return false;
        }

        protected boolean set(Task task) {
            return set(task, null);
        }
    }

    public static class AliasAttributeResolver extends AttributeResolver {
        protected String realName;

        public AliasAttributeResolver(String alias, Class<?> type, String realName, Object defaultValue) {
            super(alias, type, defaultValue);
            this.realName = realName;
        }

        @Override
        protected boolean set(Task task, Object value) {
            try {
                Field field = ClassReflection.getField(task.getClass(), realName);
                field.set(task, value == null ? defaultValue : value);
                return true;
            } catch (ReflectionException e) {
                e.printStackTrace();
            }
            return false;
        }
    }

    // from BehaviorTreeParser
    @SuppressWarnings({"unchecked", "rawtypes"})
    public Object castValue (Class<?> type, Object value) {
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
            if (type == boolean.class || type == Boolean.class) ret = value;
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

    public GameStateAdapters getGameStateAdapters() {
        return gameStateAdapters;
    }

    public DistributionAdapters getDistributionAdapters() {
        return distributionAdapters;
    }

    public static class CustomTaskAdapter extends TaskAdapter {

        CustomTaskAdapter guardAdapter;
        Array<CustomTaskAdapter> childAdapters = new Array<>();
        ObjectIntMap<String> attributeToIndexMap = new ObjectIntMap<>();

        public CustomTaskAdapter(Class<? extends Task> type) {
            super(type);
        }

        @Override
        public Task createTask() {
            Task root = super.createTask();
            if (guardAdapter != null) {
                root.setGuard(guardAdapter.createTask());
            }
            for (CustomTaskAdapter adapter : childAdapters) {
                root.addChild(adapter.createTask());
            }
            for (AttributeResolver resolver : resolverMap.values()) {
                if (resolver.defaultValue != null) {
                    resolver.set(root);
                }
            }
            return root;
        }

        @Override
        Class<?> getAttributeType(String name) {
            if (resolverMap.containsKey(name)) return super.getAttributeType(name);
            if (attributeToIndexMap.containsKey(name)) {
                int i = attributeToIndexMap.get(name, -2);
                if (i == -1) {
                    return guardAdapter.getAttributeType(name);
                }
                else {
                    return childAdapters.get(i).getAttributeType(name);
                }
            }
            throw new IllegalArgumentException("Unknown Custom task argument " + name);
        }

        @Override
        boolean setAttribute(Task task, String name, Object value) {
            if (resolverMap.containsKey(name)) {
                return super.setAttribute(task, name, value);
            }
            if (attributeToIndexMap.containsKey(name)) {
                int index = attributeToIndexMap.get(name, -2);
                if (index == -1) {
                    return guardAdapter.setAttribute(task.getGuard(), name, value);
                }
                else {
                    return childAdapters.get(index).setAttribute(task.getChild(index), name, value);
                }
            }
            throw new IllegalArgumentException("Unknown Custom task argument " + name);
        }
    }

    private static class CustomTaskParser {

        private TaskLibrary taskLibrary;
        private ObjectMap<String, String> defaultArguments = new ObjectMap<>();

        public CustomTaskParser(TaskLibrary taskLibrary) {
            this.taskLibrary = taskLibrary;
        }

        public CustomTaskAdapter parse(String namespace, XmlReader.Element xml) {
            XmlReader.Element defaultsXml = xml.getChildByName("Defaults");
            return parse(namespace, xml.getChildByName("Body").getChild(0), defaultsXml == null ? null : defaultsXml.getAttributes());
        }

        public CustomTaskAdapter parse(String namespace, XmlReader.Element xml, ObjectMap<String,String> defaults) {
            defaultArguments.clear();
            defaultArguments.putAll(defaults);
            return open(xml, null);
        }

        private CustomTaskAdapter open(XmlReader.Element xml, CustomTaskAdapter parent) {


            String[] names = xml.getName().split(":");
            String namespace = names.length > 1 ? names[0] : "";
            String name = names.length > 1 ? names[1] : names[0];

            CustomTaskAdapter adapter = null;
            if (name.equals("guard")) {
                for (int i = 0; i < xml.getChildCount(); ++i) {
                    XmlReader.Element child = xml.getChild(i);
                    if (child.getChildCount() > 0 || i == xml.getChildCount()-1) {
                        adapter = createTaskAdapter(child, parent, adapter);
                        xml = child;
                        break;
                    }
                    else {
                        adapter = createTaskAdapter(child, null, adapter);
                    }
                }
            }
            else {
                adapter = createTaskAdapter(xml, parent, null);
            }
            openChildren(xml, adapter);
            return adapter;
        }

        private CustomTaskAdapter createTaskAdapter(XmlReader.Element xml, CustomTaskAdapter parent, CustomTaskAdapter guard) {
            String[] names = xml.getName().split(":");
            String namespace = names.length > 1 ? names[0] : "";
            String name = names.length > 1 ? names[1] : names[0];

            Class<? extends Task> taskType = taskLibrary.getTaskAdapter(namespace, name).type;
            CustomTaskAdapter adapter = new CustomTaskAdapter(taskType);
            Field[] fields = ClassReflection.getFields(taskType);

            for (Field field : fields) {
                Annotation annotation = field.getDeclaredAnnotation(TaskAttribute.class);
                if (annotation == null) continue;
                String attributeName = annotation.getAnnotation(TaskAttribute.class).name();
                if (attributeName == null || attributeName.length() == 0) attributeName = field.getName();
                String value = xml.getAttribute(attributeName, null);
                if (value != null) {
                    if (value.startsWith("@")) {
                        String rootAttributeName = value.length() > 1 ? value.substring(1) : attributeName;
                        adapter.addAttributeResolver(rootAttributeName, new AliasAttributeResolver(value, field.getType(), attributeName, taskLibrary.castValue(field.getType(), XmlBehaviorTreeParser.getValue(defaultArguments.get(rootAttributeName)))));
//                        if (parent != null) {
//                            parent.attributeToIndexMap.put(rootAttributeName, parent.childAdapters.size);
//                        }
                    }
                    else {
                        adapter.addAttributeResolver(attributeName, new AttributeResolver(attributeName, field.getType(), taskLibrary.castValue(field.getType(), XmlBehaviorTreeParser.getValue(value))));
                    }
                }
                else {
//                    adapter.addAttributeResolver(attributeName, new AttributeResolver("@" + attributeName, field.getType()));
//                    if (parent != null) {
//                        parent.attributeToIndexMap.put(attributeName, parent.childAdapters.size);
//                    }
                }
            }

            if (guard != null) {
                for (ObjectMap.Entry<String, AttributeResolver> entry : guard.resolverMap) {
                    if (entry.value.name.startsWith("@")) {
                        adapter.attributeToIndexMap.put(entry.key, -1);
                    }
                }
                for (ObjectIntMap.Entry<String> entry : guard.attributeToIndexMap) {
                    if (entry.value == -1) {
                        adapter.attributeToIndexMap.put(entry.key, -1);
                    }
                }
                adapter.guardAdapter = guard;
            }

            return adapter;
        }

        private void openChildren(XmlReader.Element xml, CustomTaskAdapter parent) {
            for (int i = 0; i < xml.getChildCount(); ++i) {
                CustomTaskAdapter adapter = open(xml.getChild(i), parent);

                if (parent != null) {
                    for (ObjectIntMap.Entry<String> entry : adapter.attributeToIndexMap.entries()) {
                        parent.attributeToIndexMap.put(entry.key, parent.childAdapters.size);
                    }
                    for (ObjectMap.Entry<String,AttributeResolver> entry : adapter.resolverMap.entries()) {
                        if (entry.value.name.startsWith("@")) {
                            parent.attributeToIndexMap.put(entry.key, parent.childAdapters.size);
                        }
                    }
                    parent.childAdapters.add(adapter);
                }
            }
        }
    }

    private static final Class<?>[] CLONEABLE_TASKS = new Class<?>[] {
            DynamicGuardSelector.class,
            RandomSelector.class,
            RandomSequence.class,
            Selector.class,
            Sequence.class,
            AlwaysFail.class,
            AlwaysSucceed.class,
            Invert.class,
            UntilFail.class,
            UntilSuccess.class,
            Failure.class,
            Success.class,

            BehaviorTree.class,
            PoolableBehaviorTree.class,
            BehaviorSequence.class,
    };

    public final TaskCloner taskCloner = new TaskCloner() {
        @SuppressWarnings("unchecked")
        @Override
        public <T> Task<T> cloneTask(Task<T> task) {
            Class<? extends Task> type = task.getClass();
            try {
                Pool<? extends Task> pool = taskPoolMap.get(type);
                if (pool == null) {
                    pool = Pools.get(type);
                    taskPoolMap.put(type, pool);
                }
                Task<T> newTask = pool.obtain();
                if (task instanceof LibraryTask) {
                    ((LibraryTask<T>) task).copyToTask(newTask);
                    if (task.getGuard() != null) {
                        newTask.setGuard(task.getGuard().cloneTask());
                    }
                    return newTask;
                }

                if (type == Parallel.class) {
                    ((Parallel) newTask).policy = ((Parallel) task).policy;
                    ((Parallel) newTask).orchestrator = ((Parallel) task).orchestrator;
                }
                else if (type == Include.class) {
                    ((Include) newTask).subtree = ((Include) task).subtree;
                    ((Include) newTask).lazy = ((Include) task).lazy;
                }
                else if (type == Random.class) {
                    ((Random) newTask).success = ((Random) task).success;
                }
                else if (type == Repeat.class) {
                    ((Repeat) newTask).times = ((Repeat) task).times;
                }
                else if (type == SemaphoreGuard.class) {
                    ((SemaphoreGuard) newTask).name = ((SemaphoreGuard) task).name;
                }
                else if (type == Wait.class) {
                    ((Wait) newTask).seconds = ((Wait) task).seconds;
                }
                else {
                    boolean found = false;
                    for (Class<?> clazz : CLONEABLE_TASKS) {
                        if (clazz == type) {
                            found = true;
                            break;
                        }
                    }
                    if (!found) throw new IllegalArgumentException("Unknown task " + type.getSimpleName());
                }
                if (task.getGuard() != null) {
                    newTask.setGuard(task.getGuard().cloneTask());
                }
                for (int i = 0; i < task.getChildCount(); ++i) {
                    newTask.addChild(task.getChild(i).cloneTask());
                }
                return newTask;
            }
            catch (Throwable t) {
                throw new GdxRuntimeException("Unable to clone task " + type.getSimpleName(), t);
            }
        }

        @Override
        public <T> void freeTask(Task<T> task) {
            if (task.getGuard() != null) freeTask(task.getGuard());
            for (int i = 0; i < task.getChildCount(); ++i) {
                freeTask(task.getChild(i));
            }
            Pool<?> pool = taskPoolMap.get(task.getClass());
            if (pool != null) ((Pool<Task<T>>) pool).free(task);
        }
    };

    // DEBUG

    public void getPoolCounts(ObjectMap<String,String> mapOut) {
        for (ObjectMap.Entry<String,ObjectMap<String,TaskAdapter>> entry : namespaces) {
            for (ObjectMap.Entry<String,TaskAdapter> entry1 : entry.value) {
                Pool<?> pool = entry1.value.pool;
                mapOut.put(entry1.value.type.getSimpleName(), "" + pool.getFree() + "/" + pool.peak + "/" + pool.max);
            }
        }
    }
}
