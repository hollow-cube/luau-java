package net.hollowcube.luau;

public enum LuaCoStatus {
    /// running
    RUNNING,
    /// suspended
    SUSPENDED,
    /// 'normal' (it resumed another coroutine)
    NORMAL,
    /// finished
    FINISHED,
    /// finished with error
    ERROR;

    private static final LuaCoStatus[] VALUES = values();

    public static LuaCoStatus byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : SUSPENDED;
    }

    public int id() {
        return ordinal();
    }
}
