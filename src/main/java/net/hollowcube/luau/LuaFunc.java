package net.hollowcube.luau;

import org.jetbrains.annotations.NotNull;

/**
 * Low level representation of a native function
 */
public interface LuaFunc {
    /**
     * Preallocates a C function for use in Lua in global memory.
     *
     * <p>This is useful if a c function needs to be returned as a lua value (rather than allocating multiple times).</p>
     *
     * @param func The LuaFunc impl to allocate
     * @return A LuaFunc that is preallocated in global memory
     */
    static @NotNull LuaFunc preallocate(@NotNull LuaFunc func) {
        return new LuaFuncRef(func);
    }

    int call(@NotNull LuaState state);

}
