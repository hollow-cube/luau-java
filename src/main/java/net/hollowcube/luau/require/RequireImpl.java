package net.hollowcube.luau.require;

import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaStatus;
import net.hollowcube.luau.internal.vm.lua_CFunction;
import net.hollowcube.luau.internal.vm.lua_Continuation;
import net.hollowcube.luau.internal.vm.lua_Debug;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.util.Locale;

import static net.hollowcube.luau.LuaState.REGISTRY_INDEX;
import static net.hollowcube.luau.LuaState.upvalueIndex;
import static net.hollowcube.luau.internal.vm.lua_h.lua_getinfo;
import static net.hollowcube.luau.internal.vm.luawrap_h.luaW_pushcclosurek;

final class RequireImpl {
    private static final String REQUIRED_CACHE_TABLE_KEYS = "_MODULES";
    private static final String REGISTERED_CACHE_TABLE_KEY = "_REGISTEREDMODULES";

    private static final int REQUIRE_STACK_VALUES = 4;

    private static final MemorySegment REQUIRE_DEBUG_NAME = Arena.global().allocateFrom("require");
    private static final MemorySegment REQUIRE_IMPL = lua_CFunction.allocate(
            L -> requireImpl(LuaState.wrap(L)),
            Arena.global()
    );
    private static final MemorySegment REQUIRE_CONTINUATION_IMPL = lua_Continuation.allocate(
            (L, status) -> requireContinuationImpl(LuaState.wrap(L), LuaStatus.byId(status)),
            Arena.global()
    );

    public static void pushRequireClosure(LuaState state, RequireConfiguration<?> lrc) {
        state.newUserData(lrc);
        luaW_pushcclosurek(state.L(), REQUIRE_IMPL, REQUIRE_DEBUG_NAME, 1, REQUIRE_CONTINUATION_IMPL);
    }

    private static int requireImpl(LuaState state) {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment debug = lua_Debug.allocate(arena);
            final MemorySegment what = arena.allocateFrom("s");

            int level = 0;
            do {
                if (lua_getinfo(state.L(), level++, what, debug) == 0)
                    state.error("require is not supported in this context");
            } while (lua_Debug.what(debug).get(ValueLayout.JAVA_BYTE, 0) != 'L');

            final String source = lua_Debug.source(debug).getString(0);
            return requireInternal(state, source);
        }
    }

    private static int requireContinuationImpl(LuaState state, LuaStatus ignored) {
        assert state.getTop() >= REQUIRE_STACK_VALUES;
        int numResults = state.getTop() - REQUIRE_STACK_VALUES;
        final String cacheKey = state.checkString(2);

        if (numResults > 1)
            state.error("module must return a single value");

        // Cache the results
        if (numResults == 1) {
            // Initial stack state
            // (-1) result

            state.getField(REGISTRY_INDEX, REQUIRED_CACHE_TABLE_KEYS);
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
        state.setTop(1); // Discard extra arguments, we only use path

        final RequireConfiguration<?> lrc = (RequireConfiguration<?>) state.toUserData(upvalueIndex(1));
        if (lrc == null) state.error("unable to find require configuration");

        // void* ctx = lua_tolightuserdata(L, lua_upvalueindex(2));

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

        int stackValues = state.getTop();
        assert stackValues == REQUIRE_STACK_VALUES;

        // TODO why re-pop these, we have them right above...
        String chunkName = state.toString(-2);
        String loadName = state.toString(-1);

        int numResults = lrc.load(state, null, path, chunkName, loadName);
        if (numResults == -1) {
            if (state.getTop() != stackValues)
                state.error("stack cannot be modified when require yields");
            return state.yield(0);
        }

        return requireContinuationImpl(state, LuaStatus.OK);
    }

    private sealed interface ResolvedRequire {
        record Cached() implements ResolvedRequire {}

        record ModuleRead(String chunkName, String loadName, String cacheKey) implements ResolvedRequire {}

        record ErrorReported(String error) implements ResolvedRequire {}
    }

    private static ResolvedRequire resolveRequire(RequireConfiguration<?> lrc, LuaState state, String requirerChunkName, String path) {
        if (!lrc.isRequireAllowed(state, null, requirerChunkName))
            state.error("require is not supported in this context");

        final Navigator navigator = new Navigator(lrc, state, requirerChunkName);

        // Updates navigationContext while navigating through the given path.
        final Navigator.Status status = navigator.navigate(path);
        if (status instanceof Navigator.Status.ErrorReported(String error))
            return new ResolvedRequire.ErrorReported(error);

        if (!lrc.isModulePresent(state, null))
            return new ResolvedRequire.ErrorReported("no module present at resolved path");

        final String cacheKey = lrc.getCacheKey(state, null);
        if (cacheKey == null)
            return new ResolvedRequire.ErrorReported("could not get cache key for module");

        if (isCached(state, cacheKey)) {
            // Put cached result on top of stack before returning.
            state.getField(REGISTRY_INDEX, REQUIRED_CACHE_TABLE_KEYS);
            state.getField(-1, cacheKey);
            state.remove(-2); // remove cache table
            return new ResolvedRequire.Cached();
        }

        final String chunkName = lrc.getChunkName(state, null);
        if (chunkName == null)
            return new ResolvedRequire.ErrorReported("could not get chunkname for module");

        final String loadName = lrc.getLoadName(state, null);
        if (loadName == null)
            return new ResolvedRequire.ErrorReported("could not get loadname for module");

        return new ResolvedRequire.ModuleRead(chunkName, loadName, cacheKey);
    }

    private static boolean isCached(LuaState state, String cacheKey) {
        state.findTable(REGISTRY_INDEX, REQUIRED_CACHE_TABLE_KEYS, 1);
        state.getField(-1, cacheKey);
        boolean cached = !state.isNil(-1);
        state.pop(2); // Remove table & value
        return cached;
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
