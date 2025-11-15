package net.hollowcube.luau;

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
    ;

    private static final LuaType[] VALUES = values();

    public static LuaType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id + 1] : NONE;
    }

    public int id() {
        return ordinal() - 1;
    }

    public String typeName() {
        return name().toLowerCase();
    }
}
