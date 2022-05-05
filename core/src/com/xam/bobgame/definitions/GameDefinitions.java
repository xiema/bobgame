package com.xam.bobgame.definitions;

import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Null;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.XmlReader;
import com.badlogic.gdx.utils.reflect.ClassReflection;
import com.badlogic.gdx.utils.reflect.Constructor;
import com.badlogic.gdx.utils.reflect.ReflectionException;
import com.esotericsoftware.minlog.Log;

public class GameDefinitions {

    private static final ObjectMap<Class<? extends GameDefinition>, String> xmlPaths = new ObjectMap<>();
    static {
        xmlPaths.put(MapDefinition.class, "MapDefinitions.xml");
    }

    private static final Class<?>[] definitionClasses = new Class<?>[] {
            MapDefinition.class,
    };
    
    private ObjectMap<String, XmlReader.Element> xmls = new ObjectMap<>();

    private final ObjectMap<Class<? extends GameDefinition>, ObjectMap<String, GameDefinition>> definitionsMap = new ObjectMap<>();

    private FileHandleResolver resolver;

    private Array<GameDefinition> loadedDefinitions = new Array<>();

    private ObjectMap<String, String> textMap = new ObjectMap<>();

    private XmlReader xmlReader = new XmlReader();

    public GameDefinitions() {
        this(new InternalFileHandleResolver());
    }

    public GameDefinitions(FileHandleResolver resolver) {
        this.resolver = resolver;
    }

    public void loadXmlFiles() {
        for (ObjectMap.Entry<Class<? extends GameDefinition>, String> entry : xmlPaths) {
            xmls.put(entry.value, xmlReader.parse(resolver.resolve(entry.value)));
        }
    }

    /**
     * Create all definitions. Should be called at app start.
     */
    public void createDefinitions(boolean hotswap) {
        loadXmlFiles();
//        definitionsMap.clear();
        for (Class<? extends GameDefinition> type : xmlPaths.keys()) {
            ObjectMap<String, GameDefinition> newDefinitions = createDefinitionsForType(type);
            if (newDefinitions == null) continue;
            ObjectMap<String, GameDefinition> oldDefinitions = definitionsMap.put(type, newDefinitions);
            if (hotswap && oldDefinitions != null) {
                for (ObjectMap.Entry<String, GameDefinition> entry : oldDefinitions.entries()) {
                    if (newDefinitions.containsKey(entry.key)) {
                        if (entry.value.isLoaded()) {
                            entry.value.unload();
                            loadedDefinitions.removeValue(entry.value, true);
                        }
                    }
                    else {
                        newDefinitions.put(entry.key, entry.value);
                        Log.debug("GameDefinitions", "Reusing " + type.getSimpleName() + " for id " + entry.key);
                    }
                }
            }
        }
    }

    /**
     * Create definitions of a GameDefinition type.
     */
    private <D extends GameDefinition> ObjectMap<String, GameDefinition> createDefinitionsForType(Class<D> type) {
        // get loaded xml file
        XmlReader.Element xml = xmls.get(xmlPaths.get(type));
        if (xml == null) {
            Log.error("GameDefinitions", "XML for " + type.getSimpleName() + " not loaded");
            return null;
        }

        // get definition constructor
        Constructor constructor;
        try {
            constructor = ClassReflection.getConstructor(type, XmlReader.Element.class, GameDefinitions.class);
        } catch (ReflectionException e) {
            Log.error("GameDefinitions", "Failed to get constructor for " + type.getSimpleName());
            return null;
        }

        // create and map definitions
        ObjectMap<String, GameDefinition> definitions = new ObjectMap<>();
        for (int i = 0, n = xml.getChildCount(); i < n; ++i) {
            XmlReader.Element child = xml.getChild(i);
            try {
                @SuppressWarnings("unchecked")
                D definition = (D) constructor.newInstance(child, this);
                definitions.put(definition.id, definition);
            } catch (ReflectionException e) {
                e.printStackTrace();
                Log.error("GameDefinitions", "Failed to create " + type.getSimpleName() + " at " + i);
            }
        }

        return definitions;
    }

    // TODO: Try to load asynchronously
    public void preloadDefinitions(Class<?>[] definitionClasses) {
        int errors = 0;
        for (int i = 0, n = definitionClasses.length; i < n; ++i) {
            errors = 0;

            for (Class<?> clazz : definitionClasses) {
                @SuppressWarnings("unchecked")
                ObjectMap<String, ? extends GameDefinition> definitions = definitionsMap.get((Class<? extends GameDefinition>) clazz);
                if (definitions != null) {
                    for (GameDefinition definition : definitions.values()) {
                        try {
                            if (!definition.isLoaded()) {
                                definition.load();
                                loadedDefinitions.add(definition);
                            }
                        } catch (UnloadedDefinitionException e) {
                            Log.debug("GameDefinitions.preloadDefinitions", e.getMessage());
                            errors++;
                        }
                    }
                }
                else {
                    Log.error("GameDefinitions.preloadDefinitions", "Definition map not loaded for " + clazz.getSimpleName());
                }
            }

            if (errors == 0) {
                break;
            }
        }

        if (errors != 0) {
            Log.error("GameDefinitions.loadDefinitions", "Failed to load all Definitions!");
        }
    }

    public void preload() {
        preloadDefinitions(definitionClasses);
    }

    public void unloadLoadedDefinitions() {
        for (GameDefinition definition : loadedDefinitions) {
            definition.unload();
        }
        loadedDefinitions.clear();
    }

    public void unloadLevelDefinitions() {
//        for (Class<?> type : levelDefinitionClasses) {
//            unloadDefinitions(type);
//        }
        unloadLoadedDefinitions();
        // Don't know how to unload AI yet
    }

    public void unloadOverworldDefinitions() {
//        for (Class<?> type : overworldDefinitionClasses) {
//            unloadDefinitions(type);
//        }
        unloadLoadedDefinitions();
        // Don't know how to unload AI yet
    }

    public boolean unloadDefinition(GameDefinition definition) {
        if (definition.isLoaded()) {
            definition.unload();
            loadedDefinitions.removeValue(definition, true);
            return true;
        }
        return false;
    }
    public @Null
    <D extends GameDefinition> D getDefinition(String id, Class<D> type) {
        return getDefinition(id, type, true);
    }

    /**
     * Get a GameDefinition of a given id and type.
     */
    @SuppressWarnings("unchecked")

    public @Null <D extends GameDefinition> D getDefinition(String id, Class<D> type, boolean autoLoad) {
        ObjectMap<String, D> map = (ObjectMap<String, D>) definitionsMap.get(type);
        D definition = null;
        if (map != null) {
            definition = map.get(id);
        }

        if (definition == null) {
            throw new UnloadedDefinitionException(type.getSimpleName() + " for id " + id + " not found");
        }

        if (autoLoad && !definition.isLoaded()) {
            definition.load();
            loadedDefinitions.add(definition);
        }
        return definition;
    }


    /**
     * Get the Definition ObjectMap for a GameDefinition type.
     */
    @SuppressWarnings("unchecked")
    public @Null <D extends GameDefinition> ObjectMap<String, D> getDefinitions(Class<D> type) {
        if (!definitionsMap.containsKey(type)) {
            return null;
        }

        return (ObjectMap<String, D>) definitionsMap.get(type);
    }

    public String getText(String id) {
        return textMap.get(id);
    }

    public static class UnloadedDefinitionException extends RuntimeException {
        public UnloadedDefinitionException() {
            super();
        }

        public UnloadedDefinitionException(String s) {
            super(s);
        }

        public UnloadedDefinitionException(String s, Throwable throwable) {
            super(s, throwable);
        }

        public UnloadedDefinitionException(Throwable throwable) {
            super(throwable);
        }
    }
}
