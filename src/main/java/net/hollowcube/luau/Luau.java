package net.hollowcube.luau;

import org.jetbrains.annotations.NotNull;

public final class Luau {

    public static @NotNull LuaState newState() {
        return new LuaStateImpl();
    }
}
