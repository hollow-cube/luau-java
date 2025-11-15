package net.hollowcube.luau;

import java.lang.foreign.Arena;
import net.hollowcube.luau.internal.vm.lua_Alloc;

public sealed interface LuaAlloc permits LuaStateImpl.AllocImpl {
    @FunctionalInterface
    interface Function extends lua_Alloc.Function {}

    static LuaAlloc allocate(Function function, Arena arena) {
        return new LuaStateImpl.AllocImpl(function, arena);
    }
}
