package net.hollowcube.luau.require;

import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaStateParam;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;

import static net.hollowcube.luau.TestHelpers.eval;

@LuaStateParam
class TestRequire {

    @Test
    void def(LuaState state, Arena arena) {
        state.openLibs();

        state.openRequire(new RequireResolver() {
            @Override
            public Result reset(LuaState state, String requirerChunkName) {
                System.out.println("reset to " + requirerChunkName);
                return Result.PRESENT;
            }

            @Override
            public Result toParent(LuaState state) {
                System.out.println("toparent");
                return Result.NOT_FOUND;
            }

            @Override
            public Result toChild(LuaState state, String name) {
                System.out.println("tochild " + name);
                return Result.PRESENT;
            }

            @Override
            public Result jumpToAlias(LuaState state, String aliasPath) {
                return Result.NOT_FOUND;
            }

            @Override
            public Result getConfigStatus(LuaState state) {
                return Result.NOT_FOUND;
            }

            @Override
            public @Nullable String resolveAlias(LuaState state, String alias) {
                return null;
            }

            @Override
            public @Nullable Module getModule(LuaState state) {
                return null;
            }

            @Override
            public int load(LuaState state, String path, String chunkName, String loadName) {
                return 0;
            }
        });

        state.newTable();
        state.pushString("world");
        state.setField(-2, "hello");

        state.pushString("@test");
        state.requireRegisterModule();

        eval(state, """
            local mod = require('@test')
            print(mod.hello)
            """);
    }
}
