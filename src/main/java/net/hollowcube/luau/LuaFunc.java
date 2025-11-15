package net.hollowcube.luau;

import java.io.Closeable;
import java.lang.foreign.Arena;
import java.util.function.ToIntFunction;

public sealed interface LuaFunc extends Closeable permits LuaFuncImpl {

    /// Wraps a java function as a native Lua function.
    ///
    /// TODO memory allocation semantics.
    ///
    /// @param impl      the java function to wrap
    /// @param debugName the debug name of the function, shows in stacktraces.
    static LuaFunc wrap(ToIntFunction<LuaState> impl, String debugName) {
        return new LuaFuncImpl(impl, debugName, null);
    }

    /// todo document that it cant be closed if you BYO arena
    static LuaFunc wrap(ToIntFunction<LuaState> impl, String debugName, Arena arena) {
        return new LuaFuncImpl(impl, debugName, arena);
    }

    void close();

}
