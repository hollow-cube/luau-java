package net.hollowcube.luau.require;

import net.hollowcube.luau.LuaState;
import org.jetbrains.annotations.Nullable;

public interface RequireConfiguration<T> {

    /// Returns whether requires are permitted from the given chunkname.
    boolean isRequireAllowed(LuaState state, T ctx, String requirerChunkName);

    /// Resets the internal state to point at the requirer module.
    NavigationResult reset(LuaState state, T ctx, String requirerChunkName);

    /// Resets the internal state to point at an aliased module, given its exact
    /// path from a configuration file. This function is only called when an
    /// alias's path cannot be resolved relative to its configuration file.
    NavigationResult jumpToAlias(LuaState state, T ctx, String aliasPath);

    NavigationResult toParent(LuaState state, T ctx);

    NavigationResult toChild(LuaState state, T ctx, String name);

    boolean isModulePresent(LuaState state, T ctx);

    @Nullable String getChunkName(LuaState state, T ctx);

    @Nullable String getLoadName(LuaState state, T ctx);

    @Nullable String getCacheKey(LuaState state, T ctx);

    ConfigStatus getConfigStatus(LuaState state, T ctx);

    @Nullable String getAlias(LuaState state, T ctx, String alias);

    int load(LuaState state, T ctx, String path, String chunkName, String loadName);

}
