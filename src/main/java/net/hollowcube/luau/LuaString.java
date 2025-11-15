package net.hollowcube.luau;

public sealed interface LuaString {
    record Str(String str) implements LuaString {}

    record Atom(short atom) implements LuaString {}
}
