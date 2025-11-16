package net.hollowcube.luau;

import net.hollowcube.luau.internal.require.luarequire_Configuration;
import net.hollowcube.luau.internal.require.luarequire_Configuration_init;
import net.hollowcube.luau.require.ConfigStatus;
import net.hollowcube.luau.require.NavigationResult;
import net.hollowcube.luau.require.Require;
import net.hollowcube.luau.require.RequireConfiguration;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.concurrent.atomic.AtomicInteger;

import static net.hollowcube.luau.TestHelpers.eval;
import static net.hollowcube.luau.internal.require.Require_h.luaopen_require;
import static net.hollowcube.luau.internal.require.Require_h.luarequire_registermodule;

@LuaStateParam
class TestRequire {

    @Test
    void blah(LuaState state, Arena arena) {
        var L = ((LuaStateImpl) state).L();

        AtomicInteger maxParentCalls = new AtomicInteger(3);

        var configurationInitFunc = luarequire_Configuration_init.allocate(config -> {
            luarequire_Configuration.is_require_allowed(config, luarequire_Configuration.is_require_allowed.allocate((_, _, chunkName) -> {
                System.out.println("is_require_allowed(" + chunkName.getString(0) + ")");
                return true;
            }, arena));
            luarequire_Configuration.reset(config, luarequire_Configuration.reset.allocate((_, _, chunkName) -> {
                System.out.println("rest(" + chunkName.getString(0) + ")");
                return 0;
            }, arena));
            luarequire_Configuration.jump_to_alias(config, luarequire_Configuration.jump_to_alias.allocate((_, _, path) -> {
                System.out.println("jump_to_alias(" + path.getString(0) + ")");
                return 0;
            }, arena));
            luarequire_Configuration.to_parent(config, luarequire_Configuration.to_parent.allocate((_, _) -> {
                System.out.println("to_parent");
                if (maxParentCalls.decrementAndGet() <= 0)
                    return 2;

                return 0;
            }, arena));
            luarequire_Configuration.to_child(config, luarequire_Configuration.to_child.allocate((_, _, name) -> {
                System.out.println("to_child(" + name.getString(0) + ")");
                return 0;
            }, arena));
            luarequire_Configuration.is_module_present(config, luarequire_Configuration.is_module_present.allocate((_, _) -> {
                System.out.println("is_module_present");
                return true;
            }, arena));
            luarequire_Configuration.get_chunkname(config, luarequire_Configuration.get_chunkname.allocate((_, _, buffer, bufferSize, sizeOut) -> {
                System.out.println("get_chunkname(" + bufferSize + ")");

                buffer.setString(0, "name");
                sizeOut.set(ValueLayout.JAVA_LONG, 0, 4);
                return 0;
            }, arena));
            luarequire_Configuration.get_loadname(config, luarequire_Configuration.get_loadname.allocate((_, _, buffer, bufferSize, sizeOut) -> {
                System.out.println("get_loadname(" + bufferSize + ")");

                buffer.setString(0, "name");
                sizeOut.set(ValueLayout.JAVA_LONG, 0, 4);
                return 0;
            }, arena));
            luarequire_Configuration.get_cache_key(config, luarequire_Configuration.get_cache_key.allocate((_, _, buffer, bufferSize, sizeOut) -> {
                System.out.println("get_cache_key(" + bufferSize + ")");

                buffer.setString(0, "name");
                sizeOut.set(ValueLayout.JAVA_LONG, 0, 4);
                return 0;
            }, arena));
            luarequire_Configuration.get_config_status(config, luarequire_Configuration.get_config_status.allocate((_, _) -> {
                System.out.println("get_config_status");
                return 2;
            }, arena));
            luarequire_Configuration.get_config(config, luarequire_Configuration.get_config.allocate((_, _, buffer, bufferSize, sizeOut) -> {
                System.out.println("get_config(" + bufferSize + ")");

                buffer.setString(0, """
                        {"aliases":{"test":"./myfile"}}
                        """.trim());
                sizeOut.set(ValueLayout.JAVA_LONG, 0, 32);
                return 0;
            }, arena));
            luarequire_Configuration.load(config, luarequire_Configuration.load.allocate((L2, _, _, chunkName, loadName) -> {
                System.out.println("load(" + chunkName.getString(0) + ", " + loadName.getString(0) + ")");

                new LuaStateImpl(L2).pushString("my string!!!");
                return 1;
            }, arena));
        }, arena);
        luaopen_require(L, configurationInitFunc, MemorySegment.NULL);

        state.openLibs();

        state.pushString("@test2");
//        state.newTable();
        state.pushNumber(42);
//        state.setField(-2, "answer");
        luarequire_registermodule(L);

        eval(state, """
                local mod = require('@test')
                print(tostring(mod))
                """);
    }

    @Test
    void def(LuaState state, Arena arena) {
        AtomicInteger maxParentCalls = new AtomicInteger(3);

        state.openLibs();

        Require.openRequire(state, new RequireConfiguration<Void>() {
            @Override
            public boolean isRequireAllowed(LuaState state, Void ctx, String requirerChunkName) {
                System.out.println("is_require_allowed(" + requirerChunkName + ")");
                return true;
            }

            @Override
            public NavigationResult reset(LuaState state, Void ctx, String requirerChunkName) {
                System.out.println("reset(" + requirerChunkName + ")");
                return NavigationResult.SUCCESS;
            }

            @Override
            public NavigationResult jumpToAlias(LuaState state, Void ctx, String aliasPath) {
                System.out.println("jump_to_alias(" + aliasPath + ")");
                return NavigationResult.SUCCESS;
            }

            @Override
            public NavigationResult toParent(LuaState state, Void ctx) {
                System.out.println("to_parent");
                if (maxParentCalls.decrementAndGet() <= 0)
                    return NavigationResult.NOT_FOUND;
                return NavigationResult.SUCCESS;
            }

            @Override
            public NavigationResult toChild(LuaState state, Void ctx, String name) {
                System.out.println("to_child(" + name + ")");
                return NavigationResult.SUCCESS;
            }

            @Override
            public boolean isModulePresent(LuaState state, Void ctx) {
                System.out.println("is_module_present");
                return true;
            }

            @Override
            public @Nullable String getChunkName(LuaState state, Void ctx) {
                System.out.println("get_chunkname");
                return "my_chunk_name";
            }

            @Override
            public @Nullable String getLoadName(LuaState state, Void ctx) {
                System.out.println("get_loadname");
                return "my_load_name";
            }

            @Override
            public @Nullable String getCacheKey(LuaState state, Void ctx) {
                System.out.println("get_cache_key");
                return "my_cache_key";
            }

            @Override
            public ConfigStatus getConfigStatus(LuaState state, Void ctx) {
                System.out.println("get_config_status");
                return ConfigStatus.PRESENT_JSON;
            }

            @Override
            public @Nullable String getAlias(LuaState state, Void ctx, String alias) {
                System.out.println("get_alias(" + alias + ")");
                return "./myfile";
            }

            @Override
            public int load(LuaState state, Void ctx, String path, String chunkName, String loadName) {
                System.out.println("load(" + path + ", " + chunkName + ", " + loadName + ")");

                state.pushString("my string!!!");
                return 1;
            }
        }, null);

        eval(state, """
                local mod = require('./my.luau')
                print(tostring(mod))
                """);
    }
}
