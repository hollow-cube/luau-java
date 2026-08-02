package net.hollowcube.luau;

import org.jetbrains.annotations.ApiStatus;

import java.io.Closeable;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.function.ToIntFunction;

public sealed interface LuaFunc extends Closeable permits LuaFuncImpl {
    /// Wraps a java function as a native Lua function.
    ///
    /// TODO memory allocation semantics.
    ///
    /// @param impl      the java function to wrap
    /// @param debugName the debug name of the function, shows in stacktraces.
    static LuaFunc wrap(ToIntFunction<LuaState> impl, String debugName) {
        return new LuaFuncImpl(impl, null, debugName, null);
    }

    /// todo document that it cant be closed if you BYO arena
    static LuaFunc wrap(
        ToIntFunction<LuaState> impl,
        String debugName,
        Arena arena
    ) {
        return new LuaFuncImpl(impl, null, debugName, arena);
    }

    /// Resumes a [#yieldable] function once the call it made through
    /// [LuaState#pcallYieldable(int, int)] has settled, however it settled.
    ///
    /// The Java frame which started that call is long gone by then, so nothing can be
    /// carried across in local variables - put it in stack slots, upvalues or thread data.
    @ApiStatus.Experimental
    interface Continuation {
        /// @param status [LuaStatus#OK] if the call returned normally, in which case its
        ///               results are on the stack; otherwise the error value is
        /// @return the number of results, as for an ordinary function
        int resume(LuaState state, LuaStatus status);
    }

    /// Wraps a java function which is allowed to call Lua code that yields.
    ///
    /// A [#wrap] function cannot: a Java frame is not suspendable, so a yield underneath
    /// one would have nothing to resume into. This variant registers `continuation` with
    /// the closure, which is what makes [LuaState#pcallYieldable(int, int)] legal - the VM
    /// parks the call and re-enters through the continuation once it settles.
    ///
    /// `impl` must return the result of `pcallYieldable` unchanged, since that value is how
    /// the VM is told the call yielded.
    ///
    /// ```java
    /// LuaFunc.yieldable(
    ///     state -> {
    ///         state.pushValue(1);                  // the callback to run
    ///         return state.pcallYieldable(0, 1);
    ///     },
    ///     (state, status) -> {
    ///         state.pushBoolean(status == LuaStatus.OK);
    ///         state.insert(-2);
    ///         return 2;                            // ok, result-or-error
    ///     },
    ///     "tryCall", arena);
    ///```
    @ApiStatus.Experimental
    static LuaFunc yieldable(
        ToIntFunction<LuaState> impl,
        Continuation continuation,
        String debugName,
        Arena arena
    ) {
        return new LuaFuncImpl(impl, continuation, debugName, arena);
    }

    void close();

    @Deprecated //todo remove me
    @ApiStatus.Internal
    MemorySegment funcRef();

    /// The continuation upcall, or [MemorySegment#NULL] for a [#wrap]ped function.
    @Deprecated //todo remove me
    @ApiStatus.Internal
    MemorySegment contRef();

    @Deprecated //todo remove me
    @ApiStatus.Internal
    MemorySegment debugNameRef();
}
