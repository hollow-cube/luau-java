package net.hollowcube.luau;

import org.jetbrains.annotations.NotNull;

public enum LuaType {
    NONE,

    NIL,
    BOOLEAN,

    LIGHTUSERDATA,
    NUMBER,
    VECTOR,

    STRING,

    TABLE,
    FUNCTION,
    USERDATA,
    THREAD,
    BUFFER,

    PROTO,
    UPVAL,
    DEADKEY;

    private static final LuaType[] VALUES = values();

    public static @NotNull LuaType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    public int id() {
        return ordinal() - 1;
    }
}
