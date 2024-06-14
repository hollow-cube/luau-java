package net.hollowcube.luau;

import net.hollowcube.luau.internal.vm.lua_CFunction;
import net.hollowcube.luau.internal.vm.lua_h;
import net.hollowcube.luau.internal.vm.lualib_h;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.ToIntFunction;

@SuppressWarnings("preview")
final class LuaStateImpl implements LuaState {

    private final @NotNull MemorySegment L;
    private final @Nullable Arena arena;
    private final boolean isThread;

    // isSandboxed call required
    // assertNotSandboxed() before any setglobal
    //todo if global.sandbox() prevents any globals in that state from being changed
    // then what does sandboxthread actually do?

    public LuaStateImpl(
            @NotNull MemorySegment L,
            @Nullable Arena arena,
            boolean isThread
    ) {
        this.L = L;
        this.arena = arena;
        this.isThread = isThread;
    }

    public LuaStateImpl() {
        this(lualib_h.luaL_newstate(), Arena.ofConfined(), false);
    }

    @Override
    public @NotNull MemorySegment L() {
        return L;
    }

    @Override
    public void openLibs() {
        lualib_h.luaL_openlibs(L);
    }

    @Override
    public void defineGlobalFunction(@NotNull String name, @NotNull ToIntFunction<LuaState> function) {
        final MemorySegment funcPtr = lua_CFunction.allocate(L -> {
            try (LuaState localState = new LuaStateImpl(L, null, true);) {
                return function.applyAsInt(localState);
            }
        }, Objects.requireNonNull(arena, "anonymous state"));

        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment nameStr = arena.allocateUtf8String(name);
            lua_h.lua_pushcclosurek(L, funcPtr, nameStr, 0, MemorySegment.NULL);

            lua_h.lua_setfield(L, lua_h.LUA_GLOBALSINDEX(), nameStr);
        }
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
        if (result != 0) {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment len = arena.allocate(ValueLayout.JAVA_LONG);
                MemorySegment raw = lualib_h.luaL_checklstring(L, 1, len);

                long msgLen = len.get(ValueLayout.JAVA_LONG, 0);
                byte[] msg = raw.asSlice(0, msgLen).toArray(ValueLayout.JAVA_BYTE);
                throw new RuntimeException("pcall error: " + new String(msg, StandardCharsets.UTF_8));
            }

        }
    }


    // Stack manipulation

    @Override
    public void pop(int n) {
        lua_h.lua_settop(L, -n - 1);
    }

    @Override
    public void pushInt(int value) {
        lua_h.lua_pushinteger(L, value);
    }

    @Override
    public int checkInt(int index) {
        return lualib_h.luaL_checkinteger(L, index);
    }

    // Threads

    @Override
    public @NotNull LuaState newThread() {
        return new LuaStateImpl(lua_h.lua_newthread(L),
                Arena.ofConfined(),
                true);
    }


    // Sandboxing

    @Override
    public void sandbox() {
        lualib_h.luaL_sandbox(L);
    }

    @Override
    public void sandboxThread() {
        lualib_h.luaL_sandboxthread(L);
    }

    @Override
    public void close() {
        if (!isThread) lua_h.lua_close(L);
        if (arena != null) arena.close();
    }
}
