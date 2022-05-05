package com.xam.bobgame.ai.trees;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.ai.btree.BehaviorTree;
import com.badlogic.gdx.ai.btree.Task;
import com.badlogic.gdx.ai.btree.utils.PooledBehaviorTreeLibrary;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.utils.*;
import com.esotericsoftware.minlog.Log;
import com.xam.bobgame.ai.tasks.ArriveTask;
import com.xam.bobgame.ai.tasks.SetTargetPositionTask;
import com.xam.bobgame.utils.SuffixFileHandleResolver;

/**
 * Manages the loading, creation and disposal of Gdx-ai behavior trees.
 */
public class GameBehaviorTreeLibrary extends PooledBehaviorTreeLibrary {

    private TaskLibrary taskLibrary;
    private com.xam.bobgame.ai.trees.ExtendedBehaviorTreeParser<?> parser;
    private XmlBehaviorTreeParser<?> xmlBehaviorTreeParser;

    private ObjectMap<String, BehaviorTree<?>> unpooledRepository = new ObjectMap<>();

    private FileHandleResolver directoryResolver;

    @SuppressWarnings("rawtypes")
    public GameBehaviorTreeLibrary(FileHandleResolver resolver, TaskLibrary taskLibrary, int parseDebugLevel) {
        directoryResolver = resolver;
        this.resolver = new SuffixFileHandleResolver(resolver, ".bt");
        repository = new ObjectMap<>();
        this.taskLibrary = taskLibrary;
        parser = new com.xam.bobgame.ai.trees.ExtendedBehaviorTreeParser(taskLibrary.getDistributionAdapters(), taskLibrary.getGameStateAdapters(), taskLibrary, parseDebugLevel);
        xmlBehaviorTreeParser = new XmlBehaviorTreeParser(taskLibrary);

        addLibraryTasks();
    }

    public void addLibraryTasks() {
        Class<?>[] customClasses = new Class<?>[] {
                ArriveTask.class,
                SetTargetPositionTask.class,
        };
        for (Class<?> c : customClasses) {
            String cn = c.getSimpleName();
            String alias = Character.toLowerCase(cn.charAt(0)) + (cn.length() > 1 ? cn.substring(1, cn.lastIndexOf("Task")) : "");
            //noinspection unchecked
            taskLibrary.addTask("ai:", alias, (Class<? extends Task<?>>) c);
        }
    }

    public void addCustomTasks(XmlReader.Element xml) {
        taskLibrary.addCustomTasks("ai:", xml, true);
    }

    public String getTreeReference(BehaviorTree<Entity> behaviorTree) {
        String treeReference = repository.findKey(behaviorTree, true);
        if (treeReference == null) unpooledRepository.findKey(behaviorTree, true);
        return treeReference;
    }

    @Override
    public void registerArchetypeTree(String treeReference, BehaviorTree<?> archetypeTree) {
        this.registerArchetypeTree(treeReference, archetypeTree, false);
    }

    public void registerArchetypeTree(String treeReference, BehaviorTree<?> archetypeTree, boolean pooled) {
        if (archetypeTree == null) {
            throw new IllegalArgumentException("The registered archetype must not be null.");
        }
        if (pooled) {
            repository.put(treeReference, archetypeTree);
        }
        else {
            unpooledRepository.put(treeReference, archetypeTree);
        }
    }

    public void registerArchetypeTree(String treeReference, XmlReader.Element xml, boolean pooled) {
        if (!hasArchetypeTree(treeReference)) {
            BehaviorTree<?> tree;
            if (xml.getChildCount() > 0) {
                //noinspection
                tree = xmlBehaviorTreeParser.parse(xml, true);
            }
            else {
                String text = xml.getText();
                tree = parser.parse(text, null);
            }
            registerArchetypeTree(treeReference, tree, pooled);
        }
    }

    @Override
    public boolean hasArchetypeTree(String treeReference) {
        return unpooledRepository.containsKey(treeReference) || repository.containsKey(treeReference);
    }

    @Override
    public <T> BehaviorTree<T> createBehaviorTree(String treeReference, T blackboard) {
        BehaviorTree<T> tree;
        if (unpooledRepository.containsKey(treeReference)) {
            //noinspection unchecked
            tree = (BehaviorTree<T>) unpooledRepository.get(treeReference).cloneTask();
        }
        else {
            tree = super.createBehaviorTree(treeReference, blackboard);
        }
        tree.resetTask();
        resetGuard(tree);
        tree.setObject(blackboard);
        return tree;
    }

    // call
    public static void resetGuard(Task<?> task) {
        if (task.getGuard() != null) {
            task.getGuard().resetTask();
            resetGuard(task.getGuard());
        }
        for (int i = 0; i < task.getChildCount(); ++i) {
            resetGuard(task.getChild(i));
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void disposeBehaviorTree(String treeReference, BehaviorTree<?> behaviorTree) {
        if (unpooledRepository.containsKey(treeReference)) {
            if (Task.TASK_CLONER != null) {
                Task.TASK_CLONER.freeTask(behaviorTree);
                return;
            }
//            else {
//                disposeTask(behaviorTree.getChild(0));
//            }
//            behaviorTree.reset();
//            Pools.free(behaviorTree);
        }
        if (repository.containsKey(treeReference)) {
            Pool<BehaviorTree> pool = getPool(treeReference);
            Task root = behaviorTree.getChild(0);
//            root.resetTask();
            pool.free(behaviorTree);
            behaviorTree.addChild(root);
            behaviorTree.resetTask();
            resetGuard(behaviorTree);
//            super.disposeBehaviorTree(treeReference, behaviorTree);
        }
    }

    @Override
    protected BehaviorTree<?> retrieveArchetypeTree(String treeReference) {
        BehaviorTree<?> archetypeTree = repository.get(treeReference);
        if (archetypeTree == null) {
            archetypeTree = parser.parse(resolver.resolve(treeReference), null);
            registerArchetypeTree(treeReference, archetypeTree, true);
        }
        return archetypeTree;
    }

    public TaskLibrary getTaskLibrary() {
        return taskLibrary;
    }

    public XmlBehaviorTreeParser<?> getXmlParser() {
        return xmlBehaviorTreeParser;
    }

    public com.xam.bobgame.ai.trees.ExtendedBehaviorTreeParser<?> getParser() {
        return parser;
    }

    @Override
    public void clear() {
        super.clear();
        repository.clear();
        unpooledRepository.clear();
    }

    private void disposeTask(Task<?> task) {
        if (task.getGuard() != null) disposeTask(task.getGuard());
        for (int i = 0; i < task.getChildCount(); ++i) {
            disposeTask(task.getChild(i));
        }
        Pools.free(task);
    }

    // DEBUG
    public void getPoolCounts(ObjectMap<String, String> mapOut) {
        //noinspection rawtypes
        for (ObjectMap.Entry<String, Pool<BehaviorTree>> entry : pools.entries()) {
            mapOut.put(entry.key, "" + entry.value.getFree() + "/" + entry.value.peak + "/" + entry.value.max);
        }
    }

    public Array<String> getTreeReferences() {
        Array<String> collect = new Array<>();
        repository.keys().toArray(collect);
        unpooledRepository.keys().toArray(collect);
        return collect;
    }

    public void printTree(String treeReference) {
        BehaviorTree<?> tree = repository.get(treeReference);
        if (tree == null) tree = unpooledRepository.get(treeReference);
        if (tree != null) {
            Log.info("GameBehaviorTreeLibrary.printTree", treeReference);
            com.xam.bobgame.ai.trees.ExtendedBehaviorTreeParser.printTree(tree, 0);
        }
        else {
            Log.info("GameBehaviorTreeLibrary.printTree", "Couldn't find tree " + treeReference);
        }
    }
}
