package net.hollowcube.luau;

import net.hollowcube.luau.internal.vm.lua_CFunction;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.function.ToIntFunction;

import static net.hollowcube.luau.LuaStateImpl.mergeBacktrace;

record LuaFuncImpl(
    MemorySegment funcRef,
    MemorySegment debugNameRef,
    @Nullable Arena closeableArena
) implements LuaFunc {
    LuaFuncImpl(
        ToIntFunction<LuaState> impl,
        String debugName,
        @Nullable Arena arena
    ) {
        Arena actualArena = Objects.requireNonNullElseGet(
            arena,
            Arena::ofShared
        );
        final MemorySegment funcRef = lua_CFunction.allocate(
            new CFunctionWrapper(impl),
            actualArena
        );
        final MemorySegment debugNameRef = actualArena.allocateFrom(debugName);
        this(funcRef, debugNameRef, arena == null ? actualArena : null);
    }

    @Override
    public void close() {
        Objects.requireNonNull(
            this.closeableArena,
            "LuaFuncs allocated in provided arena may not be closed."
        ).close();
    }

    @Override
    public MemorySegment debugNameRef() {
        return debugNameRef;
    }

    record CFunctionWrapper(ToIntFunction<LuaState> impl) implements
        lua_CFunction.Function {
        @Override
        public int apply(MemorySegment L) {
            final LuaState state = new LuaStateImpl(L);
            try {
                return impl.applyAsInt(state);
            }catch (Throwable t){
                return ErrorHelper.handleError(state, t);
            }
        }
    }
}
