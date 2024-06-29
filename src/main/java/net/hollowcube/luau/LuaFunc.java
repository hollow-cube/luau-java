package net.hollowcube.luau;

import org.jetbrains.annotations.NotNull;

/**
 * Low level representation of a native function
 */
public interface LuaFunc {

    int call(@NotNull LuaState state);

}
