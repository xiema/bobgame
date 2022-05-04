package com.xam.bobgame.ai.trees;

import com.badlogic.gdx.ai.GdxAI;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.annotation.TaskAttribute;
import com.badlogic.gdx.ai.btree.annotation.TaskConstraint;
import com.badlogic.gdx.ai.btree.branch.*;
import com.badlogic.gdx.ai.btree.decorator.*;
import com.badlogic.gdx.ai.btree.leaf.Failure;
import com.badlogic.gdx.ai.btree.leaf.Success;
import com.badlogic.gdx.ai.btree.leaf.Wait;
import com.badlogic.gdx.ai.btree.utils.BehaviorTreeParser;
import com.badlogic.gdx.ai.btree.utils.BehaviorTreeReader;
import com.badlogic.gdx.ai.btree.utils.DistributionAdapters;
import com.badlogic.gdx.ai.utils.random.Distribution;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.StringBuilder;
import com.badlogic.gdx.utils.*;
import com.badlogic.gdx.utils.reflect.Annotation;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Field;
import com.badlogic.gdx.utils.reflect.ReflectionException;

import java.io.InputStream;
import java.io.Reader;

public class ExtendedBehaviorTreeParser<E> extends BehaviorTreeParser<E> {

    public static final int DEBUG_NONE = 0;
    public static final int DEBUG_LOW = 1;
    public static final int DEBUG_HIGH = 2;

    private GameStateAdapters gameStateAdapters;

    private final static String TAG = "ExtendedBehaviorTreeParser";

    public int debugLevel;
    public DistributionAdapters distributionAdapters;

    private ExtendedBehaviorTreeReader<E> btReader;

    public ExtendedBehaviorTreeParser(DistributionAdapters distributionAdapters, GameStateAdapters gameStateAdapters, TaskLibrary taskLibrary, int debugLevel) {
        this.distributionAdapters = distributionAdapters;
        this.gameStateAdapters = gameStateAdapters;
        gameStateAdapters.setDistributionAdapters(distributionAdapters);
        this.debugLevel = debugLevel;
        btReader = new ExtendedBehaviorTreeReader<>(taskLibrary);
        btReader.setParser(this);
    }

    public BehaviorTree<E> parse (String string, E object) {
        btReader.parse(string);
        return createBehaviorTree(btReader.root, object);
    }

    public BehaviorTree<E> parse (InputStream input, E object) {
        btReader.parse(input);
        return createBehaviorTree(btReader.root, object);
    }

    public BehaviorTree<E> parse (FileHandle file, E object) {
        btReader.parse(file);
        return createBehaviorTree(btReader.root, object);
    }

    public BehaviorTree<E> parse (Reader reader, E object) {
        btReader.parse(reader);
        return createBehaviorTree(btReader.root, object);
    }

    protected BehaviorTree<E> createBehaviorTree (Task<E> root, E object) {
        if (debugLevel > BehaviorTreeParser.DEBUG_LOW) printTree(root, 0);
//        BehaviorTree<E> tree = new PoolableBehaviorTree<>(root, object);
//        BehaviorTree<E> tree = Pools.obtain(PoolableBehaviorTree.class);
        //noinspection unchecked
        BehaviorTree<E> tree = Pools.obtain(BehaviorTree.class);
        tree.addChild(root);
        root.setControl(tree);
        tree.setObject(object);
        return tree;
    }

    public static <E> void printTree (Task<E> task, int indent) {
        for (int i = 0; i < indent; i++)
            System.out.print(' ');
        if (task.getGuard() != null) {
            System.out.println("Guard");
            indent = indent + 2;
            printTree(task.getGuard(), indent);
            for (int i = 0; i < indent; i++)
                System.out.print(' ');
        }
        StringBuilder sb = new StringBuilder();
        sb.append(task.getClass().getSimpleName());
        sb.append(" ");
        for (Field field : ClassReflection.getFields(task.getClass())) {
            Annotation a = field.getDeclaredAnnotation(TaskAttribute.class);
            if (a != null) {
                sb.append(field.getName());
                sb.append(":");
                try {
                    sb.append(field.get(task));
                } catch (ReflectionException ignored) {}
                sb.append(" ");
            }
        }
        System.out.println(sb);
        for (int i = 0; i < task.getChildCount(); i++) {
            printTree(task.getChild(i), indent + 2);
        }
    }

    public static class ExtendedBehaviorTreeReader<E> extends BehaviorTreeReader {

        String subtreePrefix;

        private final TaskLibrary taskLibrary;
        public static final ObjectMap<String, String> DEFAULT_IMPORTS = new ObjectMap<>();
        static {
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
            }; // @on - enable libgdx formatter
            for (Class<?> c : classes) {
                String fqcn = c.getName();
                String cn = c.getSimpleName();
                String alias = Character.toLowerCase(cn.charAt(0)) + (cn.length() > 1 ? cn.substring(1) : "");
                DEFAULT_IMPORTS.put(alias, fqcn);
            }
        }

        enum Statement {
            Import("import") {
                @Override
                protected <E> void enter (ExtendedBehaviorTreeReader<E> reader, String name, boolean isGuard) {
                }

                @Override
                protected <E> boolean attribute (ExtendedBehaviorTreeReader<E> reader, String name, Object value) {
                    if (!(value instanceof String)) reader.throwAttributeTypeException(this.name, name, "String");
                    reader.addImport(name, (String)value);
                    return true;
                }

                @Override
                protected <E> void exit (ExtendedBehaviorTreeReader<E> reader) {
                    return;
                }
            },
            Subtree("subtree") {
                @Override
                protected <E> void enter (ExtendedBehaviorTreeReader<E> reader, String name, boolean isGuard) {
                }

                @Override
                protected <E> boolean attribute (ExtendedBehaviorTreeReader<E> reader, String name, Object value) {
                    if (!name.equals("name")) reader.throwAttributeNameException(this.name, name, "name");
                    if (!(value instanceof String)) reader.throwAttributeTypeException(this.name, name, "String");
                    if ("".equals(value)) throw new GdxRuntimeException(this.name + ": the name connot be empty");
                    if (reader.subtreeName != null)
                        throw new GdxRuntimeException(this.name + ": the name has been already specified");
                    reader.subtreeName = (String)value;
                    return true;
                }

                @Override
                protected <E> void exit (ExtendedBehaviorTreeReader<E> reader) {
                    if (reader.subtreeName == null)
                        throw new GdxRuntimeException(this.name + ": the name has not been specified");
                    reader.switchToNewTree(reader.subtreeName);
                    reader.subtreeName = null;
                }
            },
            Root("root") {
                @Override
                protected <E> void enter (ExtendedBehaviorTreeReader<E> reader, String name, boolean isGuard) {
                    reader.subtreeName = ""; // the root tree has empty name
                }

                @Override
                protected <E> boolean attribute (ExtendedBehaviorTreeReader<E> reader, String name, Object value) {
                    reader.throwAttributeTypeException(this.name, name, null);
                    return true;
                }

                @Override
                protected <E> void exit (ExtendedBehaviorTreeReader<E> reader) {
                    reader.switchToNewTree(reader.subtreeName);
                    reader.subtreeName = null;
                }
            },
            TreeTask(null) {
                @Override
                protected <E> void enter (ExtendedBehaviorTreeReader<E> reader, String name, boolean isGuard) {
                    // Root tree is the default one
                    if (reader.currentTree == null) {
                        reader.switchToNewTree("");
                        reader.subtreeName = null;
                    }

                    reader.openTask(name, isGuard);
                }

                @Override
                protected <E> boolean attribute (ExtendedBehaviorTreeReader<E> reader, String name, Object value) {
                    StackedTask<E> stackedTask = reader.getCurrentTask();
                    AttrInfo ai = stackedTask.metadata.attributes.get(name);
                    if (ai == null) return false;
                    boolean isNew = reader.encounteredAttributes.add(name);
                    if (!isNew) throw reader.stackedTaskException(stackedTask, "attribute '" + name + "' specified more than once");
                    Field attributeField = reader.getField(stackedTask.task.getClass(), ai.fieldName);
                    reader.setField(attributeField, stackedTask.task, value);
                    return true;
                }

                @Override
                protected <E> void exit (ExtendedBehaviorTreeReader<E> reader) {
                    if (!reader.isSubtreeRef) {
                        reader.checkRequiredAttributes(reader.getCurrentTask());
                        reader.encounteredAttributes.clear();
                    }
                }
            };

            String name;

            Statement(String name) {
                this.name = name;
            }

            protected abstract <E> void enter (ExtendedBehaviorTreeReader<E> reader, String name, boolean isGuard);
            protected abstract <E> boolean attribute (ExtendedBehaviorTreeReader<E> reader, String name, Object value);
            protected abstract <E> void exit (ExtendedBehaviorTreeReader<E> reader);

        }

        protected ExtendedBehaviorTreeParser<E> btParser;

        ObjectMap<Class<?>, Metadata> metadataCache = new ObjectMap<>();

        Task<E> root;
        String subtreeName;
        Statement statement;
        private int indent;

        public ExtendedBehaviorTreeReader (TaskLibrary taskLibrary) {
            this(taskLibrary, false);
        }

        public ExtendedBehaviorTreeReader (TaskLibrary taskLibrary, boolean reportsComments) {
            super(reportsComments);
            this.taskLibrary = taskLibrary;
        }

        public ExtendedBehaviorTreeParser<E> getParser () {
            return btParser;
        }

        public void setParser (ExtendedBehaviorTreeParser<E> parser) {
            this.btParser = parser;
        }

        @Override
        public void parse (char[] data, int offset, int length) {
            debug = btParser.debugLevel > DEBUG_NONE;
            root = null;
            clear();
            super.parse(data, offset, length);

            // Pop all task from the stack and check their minimum number of children
            popAndCheckMinChildren(0);

            Subtree<E> rootTree = subtrees.get("");
            if (rootTree == null) throw new GdxRuntimeException("Missing root tree");
            root = rootTree.rootTask;
            if (root == null) throw new GdxRuntimeException("The tree must have at least the root task");

//            clear();
        }

        @Override
        protected void startLine (int indent) {
            if (btParser.debugLevel > DEBUG_LOW)
                GdxAI.getLogger().debug(TAG, lineNumber + ": <" + indent + ">");
            this.indent = indent;
        }

        private Statement checkStatement (String name) {
            if (name.equals(Statement.Import.name)) return Statement.Import;
            if (name.equals(Statement.Subtree.name)) return Statement.Subtree;
            if (name.equals(Statement.Root.name)) return Statement.Root;
            return Statement.TreeTask;
        }

        @Override
        protected void startStatement (String name, boolean isSubtreeReference, boolean isGuard) {
            if (btParser.debugLevel > DEBUG_LOW)
                GdxAI.getLogger().debug(TAG, (isGuard? " guard" : " task") + " name '" + name + "'");

            this.isSubtreeRef = isSubtreeReference;

            this.statement = isSubtreeReference ? Statement.TreeTask : checkStatement(name);
            if (isGuard) {
                if (statement != Statement.TreeTask)
                    throw new GdxRuntimeException(name + ": only tree's tasks can be guarded");
            }

            statement.enter(this, name, isGuard);
        }

        @Override
        protected void attribute (String name, Object value) {
            if (btParser.debugLevel > DEBUG_LOW)
                GdxAI.getLogger().debug(TAG, lineNumber + ": attribute '" + name + " : " + value + "'");

            boolean validAttribute = statement.attribute(this, name, value);
            if (!validAttribute) validAttribute = taskLibrary.setAttribute(getCurrentTask().task, getCurrentTask().name, name, value);
            if (!validAttribute) {
                if (statement == Statement.TreeTask) {
                    throw stackedTaskException(getCurrentTask(), "unknown attribute '" + name + "'");
                } else {
                    throw new GdxRuntimeException(statement.name + ": unknown attribute '" + name + "'");
                }
            }
        }

        private Field getField (Class<?> clazz, String name) {
            try {
                return ClassReflection.getField(clazz, name);
            } catch (ReflectionException e) {
                throw new GdxRuntimeException(e);
            }
        }

        private void setField (Field field, Task<E> task, Object value) {
            field.setAccessible(true);
            Object valueObject = castValue(field, value);
            if (valueObject == null) throwAttributeTypeException(getCurrentTask().name, field.getName(), field.getType().getSimpleName());
            try {
                field.set(task, valueObject);
            } catch (ReflectionException e) {
                throw new GdxRuntimeException(e);
            }
        }

        /**
         * Convert serialized value to java value.
         * Parsed value must be assignable to field argument.
         * Subclasses may override this method to parse unsupported types.
         * @param field task attribute field
         * @param value unparsed value (can be Number, String or Boolean)
         * @return parsed value or null if field type is not supported.
         */
        protected Object castValue (Field field, Object value) {
            Class<?> type = field.getType();
            Object ret = null;

            if (ClassReflection.isAssignableFrom(GameState.class, type)) {
                Class<GameState<E>> gameStateType = (Class<GameState<E>>)type;
                if (value instanceof Number) {
                    Number numberValue = (Number) value;
                    return btParser.gameStateAdapters.toGameState("constant," + numberValue, gameStateType);
                }
                else if (value instanceof String) {
                    String stringValue = (String) value;
                    return btParser.gameStateAdapters.toGameState(stringValue, gameStateType);
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
                    ret = btParser.distributionAdapters.toDistribution("constant," + numberValue, distributionType);
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
                    ret = btParser.distributionAdapters.toDistribution(stringValue, distributionType);
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

        private void throwAttributeNameException (String statement, String name, String expectedName) {
            String expected = " no attribute expected";
            if (expectedName != null)
                expected = "expected '" + expectedName + "' instead";
            throw new GdxRuntimeException(statement + ": attribute '" + name + "' unknown; " + expected);
        }

        private void throwAttributeTypeException (String statement, String name, String expectedType) {
            throw new GdxRuntimeException(statement + ": attribute '" + name + "' must be of type " + expectedType);
        }

        @Override
        protected void endLine () {
        }

        @Override
        protected void endStatement () {
            statement.exit(this);
        }

        private void openTask (String name, boolean isGuard) {
//            try {
                Task<E> task;
                if (isSubtreeRef) {
                    task = subtreeRootTaskInstance(name);
                }
                else {
                    // MODIFIED: create task from task library first
                    //noinspection unchecked
                    task = (Task<E>) taskLibrary.createTask(name);
//                    if (task == null) {
//                        String className = getImport(name);
//                        if (className == null) className = name;
//                        @SuppressWarnings("unchecked")
//                        Task<E> tmpTask = (Task<E>)ClassReflection.newInstance(ClassReflection.forName(className));
//                        task = tmpTask;
//                    }
                }

                if (!currentTree.inited()) {
                    initCurrentTree(task, indent);
                    indent = 0;
                } else if (!isGuard) {
                    StackedTask<E> stackedTask = getPrevTask();

                    indent -= currentTreeStartIndent;
                    if (stackedTask.task == currentTree.rootTask) {
                        step = indent;
                    }
                    if (indent > currentDepth) {
                        stack.add(stackedTask); // push
                    } else if (indent <= currentDepth) {
                        // Pop tasks from the stack based on indentation
                        // and check their minimum number of children
                        int i = (currentDepth - indent) / step;
                        popAndCheckMinChildren(stack.size - i);
                    }

                    // Check the max number of children of the parent
                    StackedTask<E> stackedParent = stack.peek();
                    int maxChildren = stackedParent.metadata.maxChildren;
                    if (stackedParent.task.getChildCount() >= maxChildren)
                        throw stackedTaskException(stackedParent, "max number of children exceeded ("
                                + (stackedParent.task.getChildCount() + 1) + " > " + maxChildren + ")");

                    // Add child task to the parent
                    stackedParent.task.addChild(task);
                }
                updateCurrentTask(createStackedTask(name, task), indent, isGuard);
//            } catch (ReflectionException e) {
//                throw new GdxRuntimeException("Cannot parse behavior tree!!!", e);
//            }
        }

        private StackedTask<E> createStackedTask (String name, Task<E> task) {
            Metadata metadata = findMetadata(task.getClass());
            if (metadata == null)
                throw new GdxRuntimeException(name + ": @TaskConstraint annotation not found in '" + task.getClass().getSimpleName()
                        + "' class hierarchy");
            return new StackedTask<>(lineNumber, name, task, metadata);
        }

        private Metadata findMetadata (Class<?> clazz) {
            Metadata metadata = metadataCache.get(clazz);
            if (metadata == null) {
                Annotation tca = ClassReflection.getAnnotation(clazz, TaskConstraint.class);
                if (tca != null) {
                    TaskConstraint taskConstraint = tca.getAnnotation(TaskConstraint.class);
                    ObjectMap<String, AttrInfo> taskAttributes = new ObjectMap<>();
                    Field[] fields = ClassReflection.getFields(clazz);
                    for (Field f : fields) {
                        Annotation a = f.getDeclaredAnnotation(TaskAttribute.class);
                        if (a != null) {
                            AttrInfo ai = new AttrInfo(f.getName(), a.getAnnotation(TaskAttribute.class));
                            taskAttributes.put(ai.name, ai);
                        }
                    }
                    metadata = new Metadata(taskConstraint.minChildren(), taskConstraint.maxChildren(), taskAttributes);
                    metadataCache.put(clazz, metadata);
                }
            }
            return metadata;
        }

        protected static class StackedTask<E> {
            public int lineNumber;
            public String name;
            public Task<E> task;
            public Metadata metadata;

            StackedTask (int lineNumber, String name, Task<E> task, Metadata metadata) {
                this.lineNumber = lineNumber;
                this.name = name;
                this.task = task;
                this.metadata = metadata;
            }
        }

        private static class Metadata {
            int minChildren;
            int maxChildren;
            ObjectMap<String, AttrInfo> attributes;

            Metadata (int minChildren, int maxChildren, ObjectMap<String, AttrInfo> attributes) {
                this.minChildren = minChildren < 0 ? 0 : minChildren;
                this.maxChildren = maxChildren < 0 ? Integer.MAX_VALUE : maxChildren;
                this.attributes = attributes;
            }
        }

        private static class AttrInfo {
            String name;
            String fieldName;
            boolean required;

            AttrInfo (String fieldName, TaskAttribute annotation) {
                this(annotation.name(), fieldName, annotation.required());
            }

            AttrInfo (String name, String fieldName, boolean required) {
                this.name = name == null || name.length() == 0 ? fieldName : name;
                this.fieldName = fieldName;
                this.required = required;
            }
        }

        protected static class Subtree<E> {
            String name;  // root tree must have no name
            Task<E> rootTask;
            int referenceCount;

            Subtree() {
                this(null);
            }

            Subtree(String name) {
                this.name = name;
                this.rootTask = null;
                this.referenceCount = 0;
            }

            public void init(Task<E> rootTask) {
                this.rootTask = rootTask;
            }

            public boolean inited() {
                return rootTask != null;
            }

            public boolean isRootTree() {
                return name == null || "".equals(name);
            }

            public Task<E> rootTaskInstance () {
                if (referenceCount++ == 0) {
                    return rootTask;
                }
                return rootTask.cloneTask();
            }
        }

        static ObjectMap<String, String> userImports = new ObjectMap<>();

        ObjectMap<String, Subtree<E>> subtrees = new ObjectMap<>();
        Subtree<E> currentTree;

        int currentTreeStartIndent;
        int currentDepth;
        int step;
        boolean isSubtreeRef;
        protected StackedTask<E> prevTask;
        protected StackedTask<E> guardChain;
        protected Array<StackedTask<E>> stack = new Array<>();
        ObjectSet<String> encounteredAttributes = new ObjectSet<>();
        boolean isGuard;

        StackedTask<E> getLastStackedTask() {
            return stack.peek();
        }

        StackedTask<E> getPrevTask() {
            return prevTask;
        }

        StackedTask<E> getCurrentTask() {
            return isGuard? guardChain : prevTask;
        }

        void updateCurrentTask(StackedTask<E> stackedTask, int indent, boolean isGuard) {
            this.isGuard = isGuard;
            stackedTask.task.setGuard(guardChain == null ? null : guardChain.task);
            if (isGuard) {
                guardChain = stackedTask;
            }
            else {
                prevTask = stackedTask;
                guardChain = null;
                currentDepth = indent;
            }
        }

        void clear() {
            prevTask = null;
            guardChain = null;
            currentTree = null;
            userImports.clear();
            subtrees.clear();
            stack.clear();
            encounteredAttributes.clear();
        }

        //
        // Subtree
        //

        void switchToNewTree(String name) {
            // Pop all task from the stack and check their minimum number of children
            popAndCheckMinChildren(0);

            this.currentTree = new Subtree<>(name);
            Subtree<E> oldTree = subtrees.put(name, currentTree);
            if (oldTree != null)
                throw new GdxRuntimeException("A subtree named '" + name + "' is already defined");
        }

        void initCurrentTree(Task<E> rootTask, int startIndent) {
            currentDepth = -1;
            step = 1;
            currentTreeStartIndent = startIndent;
            this.currentTree.init(rootTask);
            prevTask = null;
        }

        Task<E> subtreeRootTaskInstance(String name) {
            Subtree<E> tree = subtrees.get(name);
            if (tree == null)
                throw new GdxRuntimeException("Undefined subtree with name '" + name + "'");
            return tree.rootTaskInstance();
        }

        //
        // Import
        //

        void addImport (String alias, String task) {
            if (task == null) throw new GdxRuntimeException("import: missing task class name.");
            if (alias == null) {
                Class<?> clazz;
                try {
                    clazz = ClassReflection.forName(task);
                } catch (ReflectionException e) {
                    throw new GdxRuntimeException("import: class not found '" + task + "'");
                }
                alias = clazz.getSimpleName();
            }
            String className = getImport(alias);
            if (className != null) throw new GdxRuntimeException("import: alias '" + alias + "' previously defined already.");
            userImports.put(alias, task);
        }

        public String getImport (String as) {
            String className = DEFAULT_IMPORTS.get(as);
            TaskLibrary.TaskAdapter taskAdapter = taskLibrary.getTaskAdapter(as);
            if (taskAdapter != null) className = taskAdapter.classPath;
            return className != null ? className : userImports.get(as);
        }

        public static String getGlobalImport (String as) {
            String className = DEFAULT_IMPORTS.get(as);
            return className != null ? className : userImports.get(as);
        }

        //
        // Integrity checks
        //

        private void popAndCheckMinChildren (int upToFloor) {
            // Check the minimum number of children in prevTask
            if (prevTask != null) checkMinChildren(prevTask);

            // Check the minimum number of children while popping up to the specified floor
            while (stack.size > upToFloor) {
                StackedTask<E> stackedTask = stack.pop();
                checkMinChildren(stackedTask);
            }
        }

        private void checkMinChildren (StackedTask<E> stackedTask) {
            // Check the minimum number of children
            int minChildren = stackedTask.metadata.minChildren;
            if (stackedTask.task.getChildCount() < minChildren)
                throw stackedTaskException(stackedTask, "not enough children (" + stackedTask.task.getChildCount() + " < " + minChildren
                        + ")");
        }

        private void checkRequiredAttributes (StackedTask<E> stackedTask) {
            // Check the minimum number of children
            ObjectMap.Entries<String, AttrInfo> entries = stackedTask.metadata.attributes.iterator();
            while (entries.hasNext()) {
                ObjectMap.Entry<String, AttrInfo> entry = entries.next();
                if (entry.value.required && !encounteredAttributes.contains(entry.key))
                    throw stackedTaskException(stackedTask, "missing required attribute '" + entry.key + "'");
            }
        }

        private GdxRuntimeException stackedTaskException(StackedTask<E> stackedTask, String message) {
            return new GdxRuntimeException(stackedTask.name + " at line " + stackedTask.lineNumber + ": " + message);
        }
    }
}
