package net.hollowcube.luau.require;

import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaStatus;
import net.hollowcube.luau.internal.vm.lua_Continuation;
import net.hollowcube.luau.internal.vm.lua_Debug;
import org.jetbrains.annotations.ApiStatus;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Locale;

import static net.hollowcube.luau.LuaState.REGISTRY_INDEX;
import static net.hollowcube.luau.LuaState.upvalueIndex;
import static net.hollowcube.luau.internal.vm.lua_h.lua_getinfo;
import static net.hollowcube.luau.internal.vm.luawrap_h.luaW_pushcclosurek;

@ApiStatus.Internal
public final class RequireImpl {
    private static final String REGISTERED_CACHE_TABLE_KEY = "_REGISTEREDMODULES";
    private static final String REQUIRED_CACHE_TABLE_KEY = "_MODULES";

    private static final LuaFunc REQUIRE_IMPL = LuaFunc.wrap(RequireImpl::requireImpl, "require");
    private static final MemorySegment REQUIRE_CONTINUATION_IMPL = lua_Continuation.allocate(
        (L, status) -> requireContinuationImpl(LuaState.wrap(L), LuaStatus.byId(status)),
        Arena.global()); //todo exception handling in continuation
    private static final MemorySegment DEBUG_WHAT = Arena.global().allocateFrom("s");

    private static final int REQUIRE_STACK_VALUES = 4;

    public static void pushRequireClosure(LuaState state, RequireResolver lrc) {
        state.newUserData(lrc);

        final MemorySegment funcRef = REQUIRE_IMPL.funcRef();
        final MemorySegment debugNameRef = REQUIRE_IMPL.debugNameRef();
        luaW_pushcclosurek(state.L(), funcRef, debugNameRef, 1, REQUIRE_CONTINUATION_IMPL);
    }

    public static void registerModule(LuaState state, String path) {
        if (path.isEmpty() || path.charAt(0) != '@')
            state.error("path must begin with '@'");

        state.findTable(REGISTRY_INDEX, REGISTERED_CACHE_TABLE_KEY, 1);

        // Make path lowercase to ensure case-insensitive matching.
        state.pushString(path.toLowerCase(Locale.ROOT));
        // Stack: [cache_table, path_key]

        state.pushValue(-3);
        // Stack: [cache_table, path_key, module_result]

        state.setTable(-3);
        // Stack: [cache_table]

        state.pop(1); // Remove cache_table
    }

    public static void clearCacheEntry(LuaState state, String cacheKey) {
        state.findTable(REGISTRY_INDEX, REQUIRED_CACHE_TABLE_KEY, 1);
        state.pushNil();
        state.setField(-2, cacheKey);
        state.pop(1); // remove cache table
    }

    public static void clearCache(LuaState state) {
        state.newTable();
        state.setField(REGISTRY_INDEX, REQUIRED_CACHE_TABLE_KEY);
    }

    private static int requireImpl(LuaState state) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment debug = lua_Debug.allocate(arena);

            int level = 0;
            do {
                if (lua_getinfo(state.L(), level++, DEBUG_WHAT, debug) == 0)
                    state.error("require is not supported in this context");
            } while (lua_Debug.what(debug).get(ValueLayout.JAVA_BYTE, 0) != 'L');

            final String source = lua_Debug.source(debug).getString(0);
            return requireInternal(state, source);
        }
    }

    private static int requireContinuationImpl(LuaState state, LuaStatus ignored) {
        assert state.top() >= REQUIRE_STACK_VALUES;
        int numResults = state.top() - REQUIRE_STACK_VALUES;
        final String cacheKey = state.checkString(2);

        if (numResults < 1)
            state.error("module must return a single value");

        // Cache the results
        if (numResults == 1) {
            // Initial stack state
            // (-1) result

            state.getField(REGISTRY_INDEX, REQUIRED_CACHE_TABLE_KEY);
            // (-2) result, (-1) cache table

            state.pushValue(-2);
            // (-3) result, (-2) cache table, (-1) result

            state.setField(-2, cacheKey);
            // (-2) result, (-1) cache table

            state.pop(1);
            // (-1) result
        }

        return numResults;
    }

    private static int requireInternal(LuaState state, String requirerChunkName) {
        state.top(1); // Discard extra arguments, we only use path

        final RequireResolver lrc = (RequireResolver) state.toUserData(upvalueIndex(1));
        if (lrc == null) state.error("unable to find require configuration");

        final String path = state.checkString(1);
        if (checkRegisteredModules(state, path))
            return 1;

        final ResolvedRequire resolvedRequire = resolveRequire(lrc, state, requirerChunkName, path);
        switch (resolvedRequire) {
            case ResolvedRequire.Cached _ -> {
                return 1;
            }
            case ResolvedRequire.ErrorReported(String error) -> state.error(error);
            case ResolvedRequire.ModuleRead(String chunkName, String loadName, String cacheKey) -> {
                // (1) path, ..., cacheKey, chunkname, loadname
                state.pushString(cacheKey);
                state.pushString(chunkName);
                state.pushString(loadName);
            }
        }

        int stackValues = state.top();
        assert stackValues == REQUIRE_STACK_VALUES;

        // TODO why re-pop these, we have them right above...
        String chunkName = state.toString(-2);
        String loadName = state.toString(-1);

        int numResults = lrc.load(state, path, chunkName, loadName);
        if (numResults == -1) {
            if (state.top() != stackValues)
                state.error("stack cannot be modified when require yields");
            return state.yield(0);
        }

        return requireContinuationImpl(state, LuaStatus.OK);
    }

    private sealed interface ResolvedRequire {
        record Cached() implements ResolvedRequire {}

        record ModuleRead(String chunkName, String loadName,
                          String cacheKey) implements ResolvedRequire {}

        record ErrorReported(String error) implements ResolvedRequire {}
    }

    private static ResolvedRequire resolveRequire(RequireResolver lrc, LuaState state, String requirerChunkName, String path) {
        if (!lrc.isRequireAllowed(state, requirerChunkName))
            state.error("require is not supported in this context");

        final Navigator navigator = new Navigator(lrc, state, requirerChunkName);

        // Updates navigationContext while navigating through the given path.
        final Navigator.Status status = navigator.navigate(path);
        if (status instanceof Navigator.Status.ErrorReported(String error))
            return new ResolvedRequire.ErrorReported(error);

        final RequireResolver.Module module = lrc.getModule(state);
        if (module == null)
            return new ResolvedRequire.ErrorReported("no module present at resolved path");

        // If module is cached already, return that version
        state.findTable(REGISTRY_INDEX, REQUIRED_CACHE_TABLE_KEY, 1);
        state.getField(-1, module.cacheKey());
        if (!state.isNil(-1)) { // The module is cached
            state.remove(-2); // remove cache table
            return new ResolvedRequire.Cached();
        }
        state.pop(2); // Remove cache table & nil

        return new ResolvedRequire.ModuleRead(
            module.chunkName(),
            module.loadName(),
            module.cacheKey());
    }

    private static boolean checkRegisteredModules(LuaState state, String path) {
        state.findTable(REGISTRY_INDEX, REGISTERED_CACHE_TABLE_KEY, 1);

        state.getField(-1, path.toLowerCase(Locale.ROOT));
        if (state.isNil(-1)) {
            state.pop(2); // Remove table & nil
            return false;
        }

        state.remove(-2); // Remove table, leave module
        return true;
    }
}
