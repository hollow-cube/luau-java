package net.hollowcube.luau.require;

import net.hollowcube.luau.LuaError;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaStateParam;
import net.hollowcube.luau.LuaType;
import net.hollowcube.luau.compiler.LuauCompiler;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.hollowcube.luau.TestHelpers.eval;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@LuaStateParam
class TestRequire {

    /// A resolver over an in-memory module tree, where a module path is a "/" separated
    /// list of components ("mod", "sub/mod") and the chunk name of a module is its path.
    static final class MemoryResolver implements RequireResolver {
        final Map<String, String> modules = new HashMap<>();
        /// Every module path passed to [#load], in order, including repeats.
        final List<String> loaded = new ArrayList<>();
        /// Every requirer chunk name the navigator reset to, in order.
        final List<String> requirers = new ArrayList<>();
        boolean requireAllowed = true;

        private final Deque<String> current = new ArrayDeque<>();

        MemoryResolver module(String path, String source) {
            modules.put(path, source);
            return this;
        }

        private String path() {
            return String.join("/", current);
        }

        @Override
        public boolean isRequireAllowed(LuaState state, String requirerChunkName) {
            return requireAllowed;
        }

        @Override
        public Result reset(LuaState state, String requirerChunkName) {
            requirers.add(requirerChunkName);
            current.clear();
            for (String component : requirerChunkName.split("/"))
                current.addLast(component);
            return Result.PRESENT;
        }

        @Override
        public Result toParent(LuaState state) {
            if (current.isEmpty()) return Result.NOT_FOUND;
            current.removeLast();
            return Result.PRESENT;
        }

        @Override
        public Result toChild(LuaState state, String name) {
            current.addLast(name);
            final String path = path();
            for (String candidate : modules.keySet())
                if (candidate.equals(path) || candidate.startsWith(path + "/"))
                    return Result.PRESENT;
            current.removeLast();
            return Result.NOT_FOUND;
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
            final String path = path();
            if (!modules.containsKey(path)) return null;
            return new Module(path, path, path);
        }

        @Override
        public int load(LuaState state, String path, String chunkName, String loadName) {
            loaded.add(loadName);
            final byte[] bytecode = assertDoesNotThrow(() ->
                    LuauCompiler.DEFAULT.compile(modules.get(loadName))
            );
            final int before = state.top();
            state.load(chunkName, bytecode);
            state.call(0, -1); // MULTRET, the module decides how much it returns
            return state.top() - before;
        }
    }

    private static MemoryResolver openRequire(LuaState state) {
        state.openLibs();
        final MemoryResolver resolver = new MemoryResolver();
        state.openRequire(resolver);
        return resolver;
    }

    //region registered modules

    @Test
    void registeredModuleIsReturnedAsIs(LuaState state) {
        openRequire(state);

        state.newTable();
        state.pushString("world");
        state.setField(-2, "hello");
        state.requireRegisterModule("@test");
        state.pop(1); // registerModule leaves the module on the stack

        eval(state, """
            local mod = require('@test')
            return mod.hello
            """, 1);

        assertEquals("world", state.toString(-1));
    }

    @Test
    void registeredModuleFromStackPath(LuaState state) {
        openRequire(state);

        state.newTable();
        state.pushNumber(42);
        state.setField(-2, "answer");
        state.pushString("@stacked");
        state.requireRegisterModule();
        state.pop(1);

        eval(state, """
            return require('@stacked').answer
            """, 1);

        assertEquals(42, state.toInteger(-1));
    }

    @Test
    void registeredModuleLookupIsCaseInsensitive(LuaState state) {
        openRequire(state);

        state.newTable();
        state.pushString("world");
        state.setField(-2, "hello");
        state.requireRegisterModule("@MiXeD");
        state.pop(1);

        eval(state, """
            return require('@mixed').hello, require('@MIXED').hello
            """, 2);

        assertEquals("world", state.toString(-2));
        assertEquals("world", state.toString(-1));
    }

    @Test
    void registeredModuleIsIdenticalAcrossRequires(LuaState state) {
        openRequire(state);

        state.newTable();
        state.requireRegisterModule("@test");
        state.pop(1);

        eval(state, """
            return rawequal(require('@test'), require('@test'))
            """, 1);

        assertTrue(state.toBoolean(-1));
    }

    @Test
    void registerModuleRejectsPathWithoutAlias(LuaState state) {
        openRequire(state);

        state.newTable();
        var thrown = assertThrows(LuaError.class, () ->
                state.requireRegisterModule("test")
        );
        assertEquals("path must begin with '@'", thrown.getMessage());
    }

    //endregion

    //region relative requires

    @Test
    void relativeRequireReturnsModuleResult(LuaState state) {
        var resolver = openRequire(state);
        resolver.module("mod", "return { value = 7 }");

        eval(state, """
            return require('./mod').value
            """, 1);

        assertEquals(7, state.toInteger(-1));
        assertEquals(List.of("mod"), resolver.loaded);
        assertEquals(List.of("test.luau"), resolver.requirers);
    }

    @Test
    void requireNavigatesIntoDirectories(LuaState state) {
        var resolver = openRequire(state);
        resolver.module("sub/deep/mod", "return 'deep'");

        eval(state, """
            return require('./sub/deep/mod')
            """, 1);

        assertEquals("deep", state.toString(-1));
    }

    @Test
    void moduleMayRequireItsSiblings(LuaState state) {
        var resolver = openRequire(state);
        resolver.module("sub/a", "return require('./b') .. '-a'");
        resolver.module("sub/b", "return 'b'");

        eval(state, """
            return require('./sub/a')
            """, 1);

        assertEquals("b-a", state.toString(-1));
        assertEquals(List.of("sub/a", "sub/b"), resolver.loaded);
        // The nested require resets to the requiring module's own chunk name.
        assertEquals(List.of("test.luau", "sub/a"), resolver.requirers);
    }

    @Test
    void moduleIsCachedByCacheKey(LuaState state) {
        var resolver = openRequire(state);
        resolver.module("mod", "return {}");

        eval(state, """
            return rawequal(require('./mod'), require('./mod'))
            """, 1);

        assertTrue(state.toBoolean(-1));
        assertEquals(List.of("mod"), resolver.loaded);
    }

    @Test
    void requireClearCacheForcesReload(LuaState state) {
        var resolver = openRequire(state);
        resolver.module("mod", "return {}");

        eval(state, """
            return require('./mod')
            """, 1);
        state.requireClearCache();
        eval(state, """
            return require('./mod')
            """, 1);

        assertEquals(List.of("mod", "mod"), resolver.loaded);
        assertEquals(LuaType.TABLE, state.type(-1));
        assertEquals(false, state.rawEqual(-1, -2));
    }

    @Test
    void requireClearCacheEntryOnlyClearsThatEntry(LuaState state) {
        var resolver = openRequire(state);
        resolver.module("a", "return {}");
        resolver.module("b", "return {}");

        eval(state, """
            local a, b = require('./a'), require('./b')
            _G.a, _G.b = a, b
            """);
        state.requireClearCacheEntry("a");
        eval(state, """
            return rawequal(require('./a'), _G.a), rawequal(require('./b'), _G.b)
            """, 2);

        assertEquals(false, state.toBoolean(-2));
        assertTrue(state.toBoolean(-1));
        assertEquals(List.of("a", "b", "a"), resolver.loaded);
    }

    @Test
    void requireClearCacheEntryFromStack(LuaState state) {
        var resolver = openRequire(state);
        resolver.module("mod", "return {}");

        eval(state, """
            return require('./mod')
            """, 1);
        state.pop(1);

        state.pushString("mod");
        state.requireClearCacheEntry();
        state.pop(1);

        eval(state, """
            return require('./mod')
            """, 1);

        assertEquals(List.of("mod", "mod"), resolver.loaded);
    }

    @Test
    void pushRequireDoesNotDefineTheGlobal(LuaState state) {
        state.openLibs();
        var resolver = new MemoryResolver();
        resolver.module("mod", "return 'pushed'");

        state.pushRequire(resolver);
        state.setGlobal("myrequire");

        eval(state, """
            return myrequire('./mod'), require
            """, 2);

        assertEquals("pushed", state.toString(-2));
        assertEquals(LuaType.NIL, state.type(-1));
    }

    //endregion

    //region errors

    // BUG: LuaState#error(String) *returns* the LuaError to be thrown (only the no-arg
    //      overload throws), and RequireImpl calls it as a statement in every one of its
    //      error paths. As a result none of the errors below are ever raised: the require
    //      either silently continues with a bad stack - aborting the VM on the assert in
    //      RequireImpl#requireInternal, which takes the whole test JVM down - or quietly
    //      returns nothing. All of these are disabled because they cannot even fail
    //      cleanly; they assert the message RequireImpl already builds.

    @Test
    void requirePathWithoutPrefixIsRejected(LuaState state) {
        openRequire(state);

        var thrown = assertThrows(LuaError.class, () -> eval(state, """
            require('mod')
            """));
        assertEquals(
                "require path must start with a valid prefix: ./, ../, or @",
                thrown.getMessage()
        );
    }

    @Test
    void requireOfUnknownChildIsRejected(LuaState state) {
        openRequire(state);

        var thrown = assertThrows(LuaError.class, () -> eval(state, """
            require('./nope')
            """));
        assertEquals(
                "could not resolve child component \"nope\"",
                thrown.getMessage()
        );
    }

    @Test
    void requireOfPathWithoutModuleIsRejected(LuaState state) {
        var resolver = openRequire(state);
        // "sub" is navigable (it has a child) but is not itself a module.
        resolver.module("sub/mod", "return 1");

        var thrown = assertThrows(LuaError.class, () -> eval(state, """
            require('./sub')
            """));
        assertEquals("no module present at resolved path", thrown.getMessage());
    }

    @Test
    void moduleWithoutAResultIsRejected(LuaState state) {
        var resolver = openRequire(state);
        resolver.module("mod", "local nothing = 1");

        var thrown = assertThrows(LuaError.class, () -> eval(state, """
            require('./mod')
            """));
        assertEquals("module must return a single value", thrown.getMessage());
    }

    @Test
    void requireIsRejectedWhenTheResolverDisallowsIt(LuaState state) {
        var resolver = openRequire(state);
        resolver.module("mod", "return 1");
        resolver.requireAllowed = false;

        var thrown = assertThrows(LuaError.class, () -> eval(state, """
            require('./mod')
            """));
        assertEquals(
                "require is not supported in this context",
                thrown.getMessage()
        );
    }

    @Test
    void registeredModulesAreCheckedBeforeTheResolver(LuaState state) {
        var resolver = openRequire(state);

        state.newTable();
        state.pushString("registered");
        state.setField(-2, "from");
        state.requireRegisterModule("@bad/prefixless");
        state.pop(1);

        // The path would otherwise fail navigation, the registered entry wins.
        eval(state, """
            return require('@bad/prefixless').from
            """, 1);

        assertEquals("registered", state.toString(-1));
        assertEquals(List.of(), resolver.requirers);
    }

    //endregion
}
