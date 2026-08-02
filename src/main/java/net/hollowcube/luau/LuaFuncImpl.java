package net.hollowcube.luau;

import net.hollowcube.luau.internal.vm.lua_CFunction;
import net.hollowcube.luau.internal.vm.lua_Continuation;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.Objects;
import java.util.function.ToIntFunction;

record LuaFuncImpl(
    MemorySegment funcRef,
    MemorySegment contRef,
    MemorySegment debugNameRef,
    @Nullable Arena closeableArena
) implements LuaFunc {
    LuaFuncImpl(
        ToIntFunction<LuaState> impl,
        LuaFunc.@Nullable Continuation continuation,
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
        final MemorySegment contRef = continuation == null
            ? MemorySegment.NULL
            : lua_Continuation.allocate(
                new ContinuationWrapper(continuation),
                actualArena
            );
        final MemorySegment debugNameRef = actualArena.allocateFrom(debugName);
        this(funcRef, contRef, debugNameRef, arena == null ? actualArena : null);
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

    record ContinuationWrapper(LuaFunc.Continuation impl) implements
        lua_Continuation.Function {
        @Override
        public int apply(MemorySegment L, int status) {
            final LuaState state = new LuaStateImpl(L);
            try {
                return impl.resume(state, LuaStatus.byId(status));
            } catch (Throwable t) {
                return ErrorHelper.handleError(state, t);
            }
        }
    }
}
