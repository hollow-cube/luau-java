package net.hollowcube.luau.require;

import net.hollowcube.luau.LuaState;

public final class Require {

    public static <T> void pushRequire(LuaState state, RequireConfiguration<T> config, T ctx) {
        RequireImpl.pushRequireClosure(state, config);
    }

    public static <T> void openRequire(LuaState state, RequireConfiguration<T> config, T ctx) {
        pushRequire(state, config, ctx);
        state.setGlobal("require");
    }

    public static void registerModule(LuaState state) {
        throw new UnsupportedOperationException("todo");
    }

    public static void clearCacheEntry(LuaState state) {
        throw new UnsupportedOperationException("todo");
    }

    public static void clearCache(LuaState state) {
        throw new UnsupportedOperationException("todo");
    }

}
