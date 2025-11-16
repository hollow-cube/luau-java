package net.hollowcube.luau.require;

import net.hollowcube.luau.LuaState;

public final class Require {

    public static void pushRequire(LuaState state, RequireResolver config) {
        RequireImpl.pushRequireClosure(state, config);
    }

    public static void openRequire(LuaState state, RequireResolver config) {
        pushRequire(state, config);
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
