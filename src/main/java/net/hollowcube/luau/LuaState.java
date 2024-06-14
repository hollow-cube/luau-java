package net.hollowcube.luau;

import org.jetbrains.annotations.NotNull;

public sealed interface LuaState extends AutoCloseable permits LuaStateImpl {

    void openLibs();

    void load(@NotNull String fileName, byte[] bytecode);

    void pcall();

    @Override
    void close();
}
