package net.hollowcube.luau;

public enum LuaStatus {
    OK,
    /// Execution yielded
    YIELD,
    /// Runtime error
    ERRRUN,
    /// Unused, preserved for legacy compatibility
    UNUSED0,
    /// Out of memory
    ERRMEM,
    /// error occurred during error handler
    ERRERR,
    /// yielded for a debug breakpoint
    BREAK;

    private static final LuaStatus[] VALUES = values();

    public static LuaStatus byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : ERRERR;
    }

    public int id() {
        return ordinal();
    }
}
