package net.hollowcube.luau;

import net.hollowcube.luau.internal.vm.lua_Alloc;

import java.lang.foreign.Arena;

public sealed interface LuaAlloc permits LuaStateImpl.AllocImpl {
    @FunctionalInterface
    interface Function extends lua_Alloc.Function {
    }

    static LuaAlloc allocate(Function function, Arena arena) {
        return new LuaStateImpl.AllocImpl(function, arena);
    }

}
