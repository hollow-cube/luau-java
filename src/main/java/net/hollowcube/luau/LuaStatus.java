package net.hollowcube.luau;

public enum LuaStatus {
    OK,
    YIELD,
    ERRRUN,
    ERRSYNTAX, // Legacy, never actually returned now that bytecode is precompiled.
    ERRMEM,
    ERRERR,
    BREAK;

    private static final LuaStatus[] VALUES = values();

    public static LuaStatus byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : ERRERR;
    }

    public int id() {
        return ordinal();
    }
    
}
