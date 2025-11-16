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

    record CFunctionWrapper(ToIntFunction<LuaState> impl) implements
        lua_CFunction.Function {
        @Override
        public int apply(MemorySegment L) {
            final LuaState state = new LuaStateImpl(L);
            try {
                return impl.applyAsInt(state);
            } catch (LuaError err) {
                // If we are OOM-ing, dont attempt to resolve a stacktrace.
                if (err.status() == LuaStatus.ERRMEM) return (
                    -100 - LuaStatus.ERRMEM.id()
                );
                // If we have no more stack space, ignore the nice error propagation and just continue throwing.
                if (!state.checkStack(1)) return -100 - err.status().id();

                return err.pushAndMark(state); // Continue unwinding
            } catch (Throwable t) {
                if (t instanceof Error e) {
                    System.err.println(
                        "An unrecoverable error occurred within a LuaFunc, the VM will crash."
                    );
                    throw e;
                }

                // We got an error in java, merge with the lua stacktrace then put it on the stack and continue.
                String message = t.getClass().getName();
                if (t.getMessage() != null) message += ": " + t.getMessage();

                final LuaError err = new LuaError(message);
                err.setStackTrace(mergeBacktrace(state, t.getStackTrace(), false));
                return err.pushAndMark(state);
            }
        }
    }
}
