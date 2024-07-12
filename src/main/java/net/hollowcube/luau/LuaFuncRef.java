package net.hollowcube.luau;

import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;

class LuaFuncRef implements LuaFunc {
    private final LuaFunc func;
    private final MemorySegment ref;

    LuaFuncRef(@NotNull LuaFunc ref) {
        this.func = Objects.requireNonNull(ref);
        this.ref = LuaStateImpl.wrapLuaFunc(ref, Arena.global());
    }

    @NotNull MemorySegment ref() {
        return this.ref;
    }

    @Override
    public int call(@NotNull LuaState state) {
        return this.func.call(state);
    }

}
