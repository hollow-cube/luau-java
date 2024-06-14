package net.hollowcube.luau;

import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemorySegment;
import java.util.function.ToIntFunction;

public sealed interface LuaState extends AutoCloseable permits LuaStateImpl {

    @NotNull MemorySegment L();

    void openLibs();

    void defineGlobalFunction(@NotNull String name, @NotNull ToIntFunction<LuaState> function);

    void load(@NotNull String fileName, byte[] bytecode);

    void pcall();

    // Stack manipulation

    void pop(int n);

    void pushInt(int value);
    int checkInt(int index);

    // Threads

    @NotNull LuaState newThread();

    // Sandboxing

    void sandbox();
    void sandboxThread();

    // Cleanup

    @Override
    void close();
}
