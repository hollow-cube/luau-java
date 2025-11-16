package net.hollowcube.luau.require;

import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaStateParam;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.util.concurrent.atomic.AtomicInteger;

import static net.hollowcube.luau.TestHelpers.eval;

@LuaStateParam
class TestRequire {

    @Test
    void def(LuaState state, Arena arena) {
        AtomicInteger maxParentCalls = new AtomicInteger(3);

        state.openLibs();

        Require.openRequire(state, new RequireResolver() {
            @Override
            public boolean isRequireAllowed(LuaState state, String requirerChunkName) {
                System.out.println("is_require_allowed(" + requirerChunkName + ")");
                return true;
            }

            @Override
            public Result reset(LuaState state, String requirerChunkName) {
                System.out.println("reset(" + requirerChunkName + ")");
                return Result.PRESENT;
            }

            @Override
            public Result jumpToAlias(LuaState state, String aliasPath) {
                System.out.println("jump_to_alias(" + aliasPath + ")");
                return Result.PRESENT;
            }

            @Override
            public Result toParent(LuaState state) {
                System.out.println("to_parent");
                if (maxParentCalls.decrementAndGet() <= 0)
                    return Result.NOT_FOUND;
                return Result.PRESENT;
            }

            @Override
            public Result toChild(LuaState state, String name) {
                System.out.println("to_child(" + name + ")");
                return Result.PRESENT;
            }

            @Override
            public @Nullable Module getModule(LuaState state) {
                System.out.println("get_module");
                return new Module("my_chunk_name", "my_load_name", "my_cache_key");
            }

            @Override
            public Result getConfigStatus(LuaState state) {
                System.out.println("get_config_status");
                return Result.PRESENT;
            }

            @Override
            public @Nullable String resolveAlias(LuaState state, String alias) {
                System.out.println("get_alias(" + alias + ")");
                return "./myfile";
            }

            @Override
            public int load(LuaState state, String path, String chunkName, String loadName) {
                System.out.println("load(" + path + ", " + chunkName + ", " + loadName + ")");

                state.pushString("my string!!!");
                return 1;
            }
        });

        eval(state, """
                local mod = require('./my.luau')
                print(tostring(mod))
                """);
    }
}
