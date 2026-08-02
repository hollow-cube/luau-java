package net.hollowcube.luau;

/// Mirrors `lua_Type` in lua.h. Ordinals are shifted by one relative to the native ids
/// so that [#NONE] (`LUA_TNONE`, -1) can be represented; see [#id()] and [#byId(int)].
///
/// Note this assumes the default single-precision vector build (`LUA_VECTOR_DOUBLE == 0`),
/// which is what the shipped natives use. A double-precision build moves `LUA_TVECTOR`
/// after `LUA_TOBJECT`.
public enum LuaType {
    NONE,

    NIL,
    BOOLEAN,

    LIGHTUSERDATA,
    NUMBER,
    INTEGER,
    VECTOR,

    STRING,

    TABLE,
    FUNCTION,
    USERDATA,
    THREAD,
    BUFFER,
    CLASS,
    OBJECT;

    private static final LuaType[] VALUES = values();

    public static LuaType byId(int id) {
        return id >= 0 && id + 1 < VALUES.length ? VALUES[id + 1] : NONE;
    }

    public int id() {
        return ordinal() - 1;
    }

    public String typeName() {
        return name().toLowerCase();
    }
}
