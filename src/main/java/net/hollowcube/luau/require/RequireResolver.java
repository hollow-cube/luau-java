package net.hollowcube.luau.require;

import net.hollowcube.luau.LuaState;
import org.jetbrains.annotations.Nullable;

public interface RequireResolver {

    enum Result { PRESENT, NOT_FOUND, AMBIGUOUS }

    record Module(String chunkName, String loadName, String cacheKey) {}

    /// Returns whether requires are permitted from the given chunk name.
    default boolean isRequireAllowed(LuaState state, String requirerChunkName) {
        return true;
    }

    /// Resets the internal state to point at the requirer module.
    Result reset(LuaState state, String requirerChunkName);
    /// Moves the internal state to the parent module.
    Result toParent(LuaState state);
    /// Moves the internal state to the named child module.
    Result toChild(LuaState state, String name);

    /// Resets the internal state to point at an aliased module, given its exact
    /// path from [#resolveAlias(LuaState, String)]. This function is only called when an
    /// alias's path cannot be resolved relative to its configuration file.
    ///
    /// For example, a .luaurc alias entry of "@utils -> remote://abc" would end up calling
    /// this function with "remote://abc" because it cannot be resolved to a relative path
    /// (e.g. starting with './' or '../'), and it is not itself an alias.
    Result jumpToAlias(LuaState state, String aliasPath);

    /// Returns the status of a config associated with the current internal state point.
    ///
    /// If [Result#NOT_FOUND], require-by-string will call [#toParent(LuaState)] until
    /// either a config is present or [Result#NOT_FOUND] is returned (at root).
    ///
    /// A canonical filesystem implementation would return [Result#PRESENT] if a
    /// .luaurc or .config.luau exists, [Result#NOT_FOUND] if neither exists, or
    /// [Result#AMBIGUOUS] if both exist.
    Result getConfigStatus(LuaState state);

    /// Returns the resolved path of the given alias in the config at the current
    /// internal state point.
    ///
    /// A canonical filesystem implementation would parse a .luaurc or .config.luau file
    /// in the current directory. This will only be called if [#getConfigStatus(LuaState)]
    /// returns [Result#PRESENT].
    ///
    /// @return A resolved alias, or null if no matching alias exists.
    @Nullable String resolveAlias(LuaState state, String alias);

    /// Returns the specification of the module which internal state is currently pointing at,
    /// or null if the internal state does not currently point to a module.
    @Nullable Module getModule(LuaState state);

    /// Executes the module and places the result on the stack.
    ///
    /// @return The number of results placed on the stack.
    ///         Returning -1 directs the requiring thread to yield.
    ///         In this case, this thread should be resumed with the module result pushed onto its stack.
    int load(LuaState state, String path, String chunkName, String loadName);

}
