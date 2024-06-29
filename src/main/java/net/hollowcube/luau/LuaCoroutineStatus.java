package net.hollowcube.luau;

public enum LuaCoroutineStatus {
    RUNNING,
    SUSPENDED,
    NORMAL,
    FINISHED,
    ERROR;

    private static final LuaCoroutineStatus[] VALUES = values();

    public static LuaCoroutineStatus byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : ERROR;
    }

    public int id() {
        return ordinal();
    }
    
}
