package net.hollowcube.luau.require;

import net.hollowcube.luau.LuaState;

public class RuntimeNavigationContext {
    private final RequireConfiguration<?> lrc;
    private final LuaState state;
    private final String requirerChunkName;

    public RuntimeNavigationContext(RequireConfiguration<?> lrc, LuaState state, String requirerChunkName) {
        this.lrc = lrc;
        this.state = state;
        this.requirerChunkName = requirerChunkName;
    }
}
