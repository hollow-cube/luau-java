package net.hollowcube.luau;

import org.jetbrains.annotations.NotNull;

import java.lang.foreign.MemorySegment;

record LuaCallbacksImpl(@NotNull MemorySegment struct) implements LuaCallbacks {

}
