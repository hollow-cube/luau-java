package net.hollowcube.luau;

import net.hollowcube.luau.internal.vm.lua_h;
import net.hollowcube.luau.internal.vm.lualib_h;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

@SuppressWarnings("preview")
record LuaStateImpl(
        @NotNull MemorySegment L
) implements LuaState {

    public LuaStateImpl() {
        this(lualib_h.luaL_newstate());
    }

    @Override
    public void openLibs() {
        lualib_h.luaL_openlibs(L);
    }

    @Override
    public void load(@NotNull String fileName, byte[] bytecode) {
        try (final Arena arena = Arena.ofConfined()) {
            final MemorySegment nameStr = arena.allocateUtf8String(fileName);
            final MemorySegment bytecodeArr = arena.allocateArray(ValueLayout.JAVA_BYTE, bytecode);

            int result = lua_h.luau_load(L, nameStr, bytecodeArr, bytecode.length, 0);
            // todo if there is an error we should extract it properly.
            if (result != 0) throw new RuntimeException("Failed to load bytecode: " + result);
        }
    }

    @Override
    public void pcall() {
        int result = lua_h.lua_pcall(L, 0, 0, 0);
        if (result != 0) throw new RuntimeException("pcall error: " + result);
    }

    @Override
    public void close() {
        lua_h.lua_close(L);
    }
}
