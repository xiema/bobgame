package com.xam.bobgame.ai.trees;

import com.badlogic.ashley.core.Engine;
import com.badlogic.gdx.ai.btree.utils.DistributionAdapters;
import com.badlogic.gdx.ai.utils.random.Distribution;
import com.badlogic.gdx.ai.utils.random.FloatDistribution;
import com.badlogic.gdx.ai.utils.random.IntegerDistribution;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.Pool;
import com.badlogic.gdx.utils.Pools;

import java.util.StringTokenizer;

/**
 * Used for injecting custom Distribution Adapters (like GameStates) into a Behavior Tree parser.
 */
@SuppressWarnings("rawtypes")
public class GameStateAdapters {

    DistributionAdapters distributionAdapters;
    Engine engine;
//    GameDefinitions definitions;

    private static final ObjectMap<Class<?>, DistributionGameStateAdapter<?>> distributionMap = new ObjectMap<>();

    public GameStateAdapters() {
        this.map = new ObjectMap<>();
        this.typeMap = new ObjectMap<>();
//        this.definitions = definitions;
        addGameStateAdapters();
//        for (ObjectMap.Entry<Class<?>, Adapter<?>> e : ADAPTERS.entries())
//            add(e.key, e.value);
    }

    public void setEngine(Engine engine) {
        this.engine = engine;
    }

    public void setDistributionAdapters(DistributionAdapters distributionAdapters) {
        this.distributionAdapters = distributionAdapters;
    }

    public DistributionAdapters getDistributionAdapters() {
        return distributionAdapters;
    }

    ObjectMap<Class<?>, GameStateAdapter<?>> map;
    ObjectMap<Class<?>, ObjectMap<String, GameStateAdapter<?>>> typeMap;

    @SuppressWarnings("unchecked")
    public <GS extends GameState> GS toGameState(String value, Class<GS> clazz) {
        StringTokenizer st = new StringTokenizer(value, ", \t\f");
        if (!st.hasMoreTokens()) throw new GameStateFormatException("Missing game state type");
        String type = st.nextToken();
        ObjectMap<String, GameStateAdapter<?>> categories = typeMap.get(clazz);
        GameStateAdapter<GS> converter = (GameStateAdapter<GS>)categories.get(type);
        if (converter == null) {
            DistributionGameStateAdapter<GS> distributionConverter = (DistributionGameStateAdapter<GS>) distributionMap.get(clazz);
            if (distributionConverter != null) return distributionConverter.toGameState(value);
        }
        if (converter == null) throw new GameStateFormatException("Cannot create a '" + clazz.getSimpleName() + "' of type '" + type + "'");
        String[] args = new String[st.countTokens()];
        for (int i = 0; i < args.length; i++)
            args[i] = st.nextToken();
        return converter.toGameState(args);
    }

    public final void add (Class<?> clazz, GameStateAdapter<?> adapter) {
        map.put(clazz, adapter);

        ObjectMap<String, GameStateAdapter<?>> m = typeMap.get(adapter.type);
        if (m == null) {
            m = new ObjectMap<>();
            typeMap.put(adapter.type, m);
        }
        m.put(adapter.category, adapter);
    }

    public abstract static class GameStateAdapter<GS extends GameState> {
        final String category;
        final Class<?> type;

        public GameStateAdapter (String category, Class<?> type) {
            this.category = category;
            this.type = type;
        }

        public abstract GS toGameState (String[] args);

        public abstract String[] toParameters (GS gameState);

        public static double parseDouble (String v) {
            try {
                return Double.parseDouble(v);
            } catch (NumberFormatException nfe) {
                throw new GameStateFormatException("Not a double value: " + v, nfe);
            }
        }

        public static float parseFloat (String v) {
            try {
                return Float.parseFloat(v);
            } catch (NumberFormatException nfe) {
                throw new GameStateFormatException("Not a float value: " + v, nfe);
            }
        }

        public static int parseInteger (String v) {
            try {
                return Integer.parseInt(v);
            } catch (NumberFormatException nfe) {
                throw new GameStateFormatException("Not an int value: " + v, nfe);
            }
        }

        public static long parseLong (String v) {
            try {
                return Long.parseLong(v);
            } catch (NumberFormatException nfe) {
                throw new GameStateFormatException("Not a long value: " + v, nfe);
            }
        }

    }

    public abstract static class DistributionGameStateAdapter<GS extends GameState> extends GameStateAdapter<GS> {
        final Class<? extends Distribution> distributionType;

        public DistributionGameStateAdapter(String category, Class<? extends GameState> gameStateType, Class<? extends Distribution> distributionType) {
            super(category, gameStateType);
            this.distributionType = distributionType;
        }

        @Override
        public GS toGameState(String[] args) {
            StringBuilder builder = new StringBuilder();
            for (String s : args) builder.append(s);
            return toGameState(builder.toString());
        }

        public abstract GS toGameState(String argString);

        @Override
        public String[] toParameters(GS gameState) {
            StringTokenizer st = new StringTokenizer(toArgString(gameState), ", \t\f");
            String[] args = new String[st.countTokens()];
            for (int i = 0; i < args.length; i++)
                args[i] = st.nextToken();
            return args;
        }

        public abstract String toArgString(GS gameState);
    }

    public abstract static class FloatGameStateAdapter<GS extends GameStates.FloatGameState> extends GameStateAdapter<GS> {
        public FloatGameStateAdapter(String category) {
            super(category, GameStates.FloatGameState.class);
        }
    }
    public abstract static class IntGameStateAdapter<GS extends GameStates.IntGameState> extends GameStateAdapter<GS> {
        public IntGameStateAdapter(String category) {
            super(category, GameStates.IntGameState.class);
        }
    }
    public abstract static class StringGameStateAdapter<GS extends GameStates.StringGameState> extends GameStateAdapter<GS> {
        public StringGameStateAdapter(String category) {
            super(category, GameStates.StringGameState.class);
        }
    }
    public abstract static class EntityGameStateAdapter<GS extends GameStates.EntityGameState> extends GameStateAdapter<GS> {
        public EntityGameStateAdapter(String category) {
            super(category, GameStates.EntityGameState.class);
        }
    }

    private void addGameStateAdapters() {

        distributionMap.put(GameStates.FloatGameState.class,
            new DistributionGameStateAdapter<GameStates.DistributionFloatGameState>("distribution", GameStates.FloatGameState.class, FloatDistribution.class) {
            @Override
            public GameStates.DistributionFloatGameState toGameState(String argString) {
                FloatDistribution distribution = (FloatDistribution) distributionAdapters.toDistribution(argString, distributionType);
                GameStates.DistributionFloatGameState gameState = Pools.obtain(GameStates.DistributionFloatGameState.class);
                gameState.distribution = distribution;
                return gameState;
            }

            public String toArgString(GameStates.DistributionFloatGameState gameState) {
                return distributionAdapters.toString(gameState.getDistribution());
            }
        });
        distributionMap.put(GameStates.IntGameState.class,
            new DistributionGameStateAdapter<GameStates.DistributionIntGameState>("distribution", GameStates.IntGameState.class, IntegerDistribution.class) {
            @Override
            public GameStates.DistributionIntGameState toGameState(String argString) {
                IntegerDistribution distribution = (IntegerDistribution) distributionAdapters.toDistribution(argString, distributionType);
                GameStates.DistributionIntGameState gameState = Pools.obtain(GameStates.DistributionIntGameState.class);
                gameState.distribution = distribution;
                return gameState;
            }

            public String toArgString(GameStates.DistributionIntGameState gameState) {
                return distributionAdapters.toString(gameState.getDistribution());
            }
        });


        add(GameStates.PlayerPositionState.class,
            new FloatGameStateAdapter<GameStates.PlayerPositionState>("player") {
            @Override
            public GameStates.PlayerPositionState toGameState(String[] args) {
                GameStates.PlayerPositionState gameState = Pools.obtain(GameStates.PlayerPositionState.class);
                gameState.engine = engine;
                switch(args.length) {
                    case 1:
                        if (args[0].equals("x") || args[0].equals("y")) {
                            gameState.axis = args[0];
                            gameState.offset = 0;
                            return gameState;
                        }
                        else {
                            throw new GameStateFormatException("Must pass 'x' or 'y' for axis (got " + args[0] + ")");
                        }
                    case 2:
                        if (args[0].equals("x") || args[0].equals("y")) {
                            gameState.axis = args[0];
                            gameState.offset = parseFloat(args[1]);
                            return gameState;
                        }
                        else {
                            throw new GameStateFormatException("Must pass 'x' or 'y' for axis (got " + args[0] + ")");
                        }
                    default:
                        throw invalidNumberOfGameStateArgumentsException(args.length, 1, 2);
                }
            }

            @Override
            public String[] toParameters(GameStates.PlayerPositionState gameState) {
                return new String[] {gameState.getAxis(), Float.toString(gameState.getOffset())};
            }
        });

        add(GameStates.BearingToEntityPositionGameState.class,
            new FloatGameStateAdapter<GameStates.BearingToEntityPositionGameState>("bearingTo") {
            @Override
            public GameStates.BearingToEntityPositionGameState toGameState(String[] args) {
                GameStates.BearingToEntityPositionGameState gameState = Pools.obtain(GameStates.BearingToEntityPositionGameState.class);
                gameState.engine = engine;
                switch(args.length) {
                    case 1:
                        gameState.whichEntity = args[0];
                        return gameState;
                    default:
                        throw invalidNumberOfGameStateArgumentsException(args.length, 1);
                }
            }

            @Override
            public String[] toParameters(GameStates.BearingToEntityPositionGameState gameState) {
                return new String[] {};
            }
        });

        add(GameStates.EntityPositionState.class,
        new FloatGameStateAdapter<GameStates.EntityPositionState>("self") {
            @Override
            public GameStates.EntityPositionState toGameState(String[] args) {
                GameStates.EntityPositionState gameState = Pools.obtain(GameStates.EntityPositionState.class);
                gameState.engine = engine;
                gameState.whichEntity = "self";
                switch(args.length) {
                    case 1:
                        if (args[0].equals("x") || args[0].equals("y")) {
                            gameState.axis = args[0];
                            gameState.offset = 0;
                            return gameState;
                        }
                        else {
                            throw new GameStateFormatException("Must pass 'x' or 'y' for axis (got " + args[0] + ")");
                        }
                    case 2:
                        if (args[0].equals("x") || args[0].equals("y")) {
                            gameState.axis = args[0];
                            gameState.offset = parseFloat(args[1]);
                            return gameState;
                        }
                        else {
                            throw new GameStateFormatException("Must pass 'x' or 'y' for axis (got " + args[0] + ")");
                        }
                    default:
                        throw invalidNumberOfGameStateArgumentsException(args.length, 1, 2);
                }
            }

            @Override
            public String[] toParameters(GameStates.EntityPositionState gameState) {
                return new String[] {gameState.getAxis(), Float.toString(gameState.getOffset())};
            }
        });

        add(GameStates.TargetPositionState.class,
        new FloatGameStateAdapter<GameStates.TargetPositionState>("target") {
            @Override
            public GameStates.TargetPositionState toGameState(String[] args) {
                GameStates.TargetPositionState gameState = Pools.obtain(GameStates.TargetPositionState.class);
                switch(args.length) {
                    case 1:
                        if (args[0].equals("x") || args[0].equals("y")) {
                            gameState.axis = args[0];
                            gameState.offset = 0;
                            return gameState;
                        }
                        else {
                            throw new GameStateFormatException("Must pass 'x' or 'y' for axis (got " + args[0] + ")");
                        }
                    case 2:
                        gameState.axis = args[0];
                        gameState.offset = parseFloat(args[1]);
                        if (args[0].equals("x") || args[0].equals("y")) {
                            return gameState;
                        }
                        else {
                            throw new GameStateFormatException("Must pass 'x' or 'y' for axis (got " + args[0] + ")");
                        }
                    default:
                        throw invalidNumberOfGameStateArgumentsException(args.length, 1, 2);
                }
            }

            @Override
            public String[] toParameters(GameStates.TargetPositionState gameState) {
                return new String[] {"self", gameState.getAxis(), Float.toString(gameState.getOffset())};
            }
        });

//        add(GameStates.DefinitionFloatGameState.class, new FloatGameStateAdapter<GameStates.DefinitionFloatGameState>("ship") {
//            @Override
//            public GameStates.DefinitionFloatGameState toGameState(String[] args) {
//                GameStates.DefinitionFloatGameState gameState = Pools.obtain(GameStates.DefinitionFloatGameState.class);
//                switch(args.length) {
//                    case 2:
//                        Field field = getDefinitionField(ShipDefinition.class, args[1], Number.class);
//                        ShipDefinition definition = null;
//                        try {
//                            definition = definitions.getDefinition(args[0], ShipDefinition.class);
//                        }
//                        catch (GameDefinitions.UnloadedDefinitionException ignored) {}
//
//                        if (field != null && definition != null) {
//                            try {
//                                float value = (float) field.get(definition);
//                                gameState.value = value;
//                                gameState.originalParameters = args;
//                                return gameState;
//                            } catch (ReflectionException e) {
//                                DebugUtils.error("GameStateAdapters", "Failed to get " + args[1] + " from " + args[0]);
//                            }
//                        }
//                        break;
//                    default:
//                        throw invalidNumberOfGameStateArgumentsException(args.length, 2);
//                }
//
//                return null;
//            }
//
//            @Override
//            public String[] toParameters(GameStates.DefinitionFloatGameState gameState) {
//                return gameState.toParameters();
//            }
//        });

        add(GameStates.FloatGameState.class, new FloatGameStateAdapter<GameStates.MemoryFloatGameState>("memory") {
            @Override
            public GameStates.MemoryFloatGameState toGameState(String[] args) {
                GameStates.MemoryFloatGameState gameState = Pools.obtain(GameStates.MemoryFloatGameState.class);
                switch(args.length) {
                    case 1:
                        gameState.name = args[0];
                        gameState.offset = 0;
                        return gameState;
                    case 2:
                        gameState.name = args[0];
                        gameState.offset = parseFloat(args[1]);
                        return gameState;
                    default:
                        throw invalidNumberOfGameStateArgumentsException(args.length, 1, 2);
                }
            }

            @Override
            public String[] toParameters(GameStates.MemoryFloatGameState gameState) {
                return new String[] {gameState.name};
            }
        });
        add(GameStates.IntGameState.class, new IntGameStateAdapter<GameStates.MemoryIntGameState>("memory") {
            @Override
            public GameStates.MemoryIntGameState toGameState(String[] args) {
                GameStates.MemoryIntGameState gameState = Pools.obtain(GameStates.MemoryIntGameState.class);
                switch(args.length) {
                    case 1:
                        gameState.name = args[0];
                        gameState.offset = 0;
                        return gameState;
                    case 2:
                        gameState.name = args[0];
                        gameState.offset = parseInteger(args[1]);
                        return gameState;
                    default:
                        throw invalidNumberOfGameStateArgumentsException(args.length, 1, 2);
                }
            }

            @Override
            public String[] toParameters(GameStates.MemoryIntGameState gameState) {
                return new String[] {gameState.name};
            }
        });
        add(GameStates.StringGameState.class, new StringGameStateAdapter<GameStates.MemoryStringGameState>("memory") {
            @Override
            public GameStates.MemoryStringGameState toGameState(String[] args) {
                GameStates.MemoryStringGameState gameState = Pools.obtain(GameStates.MemoryStringGameState.class);
                switch(args.length) {
                    case 1:
                        gameState.name = args[0];
                        return gameState;
                    default:
                        throw invalidNumberOfGameStateArgumentsException(args.length, 1);
                }
            }

            @Override
            public String[] toParameters(GameStates.MemoryStringGameState gameState) {
                return new String[] {gameState.name};
            }
        });

        add(GameStates.ConstantFloatGameState.class,
            new FloatGameStateAdapter<GameStates.ConstantFloatGameState>("constant") {
            @Override
            public GameStates.ConstantFloatGameState toGameState(String[] args) {
                GameStates.ConstantFloatGameState gameState = Pools.obtain(GameStates.ConstantFloatGameState.class);
                switch(args.length) {
                    case 1:
                        gameState.value = parseFloat(args[0]);
                        return gameState;
                    default:
                        throw invalidNumberOfGameStateArgumentsException(args.length, 1);
                }
            }

            @Override
            public String[] toParameters(GameStates.ConstantFloatGameState gameState) {
                return new String[] {Float.toString(gameState.getValue())};
            }
        });
        add(GameStates.ConstantIntGameState.class,
            new IntGameStateAdapter<GameStates.ConstantIntGameState>("constant") {
            @Override
            public GameStates.ConstantIntGameState toGameState(String[] args) {
                GameStates.ConstantIntGameState gameState = Pools.obtain(GameStates.ConstantIntGameState.class);
                switch(args.length) {
                    case 1:
                        gameState.value = parseInteger(args[0]);
                        return gameState;
                    default:
                        throw invalidNumberOfGameStateArgumentsException(args.length, 1);
                }
            }

            @Override
            public String[] toParameters(GameStates.ConstantIntGameState gameState) {
                return new String[] {Integer.toString(gameState.getValue())};
            }
        });
        add(GameStates.SpecialEntityGameState.class,
            new EntityGameStateAdapter<GameStates.SpecialEntityGameState>("special") {
            @Override
            public GameStates.SpecialEntityGameState toGameState(String[] args) {
                GameStates.SpecialEntityGameState gameState = Pools.obtain(GameStates.SpecialEntityGameState.class);
                gameState.engine = engine;
                switch(args.length) {
                    case 1:
                        gameState.type = GameStates.SpecialEntityGameState.Type.valueOf(args[0]);
                        return gameState;
                    default:
                        throw invalidNumberOfGameStateArgumentsException(args.length, 1);
                }
            }

            @Override
            public String[] toParameters(GameStates.SpecialEntityGameState gameState) {
                return new String[] {gameState.type.name()};
            }
        });
    }

//    private Field getDefinitionField(Class<? extends GameDefinition> definitionType, String fieldName, Class<?> fieldType) {
//        Field field = null;
//        try {
//            field = ClassReflection.getField(definitionType, fieldName);
//            if (!ClassReflection.isAssignableFrom(fieldType, field.getType())) field = null;
//        } catch (ReflectionException e) {
//            e.printStackTrace();
//        }
//        return field;
//    }

    private static GameStateFormatException invalidNumberOfGameStateArgumentsException (int found, int... expected) {
        String message = "Found " + found + " arguments in GameState; expected ";
        if (expected.length < 2)
            message += expected.length;
        else {
            String sep = "";
            int i = 0;
            while (i < expected.length - 1) {
                message += sep + expected[i++];
                sep = ", ";
            }
            message += " or " + expected[i];
        }
        return new GameStateFormatException(message);
    }

    public static class GameStateFormatException extends RuntimeException {
        public GameStateFormatException() {
            super();
        }
        public GameStateFormatException(String s) {
            super(s);
        }
        public GameStateFormatException(String message, Throwable cause) {
            super(message, cause);
        }
        public GameStateFormatException(Throwable cause) {
            super(cause);
        }

    }


    // DEBUG

    public void getPoolCounts(ObjectMap<String,String> mapOut) {
        for (Class<?> clazz : map.keys()) {
            Pool<?> pool = Pools.get(clazz);
            mapOut.put(clazz.getSimpleName(), "" + pool.getFree() + "/" + pool.peak + "/" + pool.max);
        }
    }
}
