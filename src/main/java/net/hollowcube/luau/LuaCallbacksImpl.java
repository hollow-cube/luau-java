package net.hollowcube.luau;

import net.hollowcube.luau.internal.vm.lua_Callbacks;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;

record LuaCallbacksImpl(MemorySegment callbacks) implements LuaCallbacks {
    record InterruptImpl(MemorySegment handle) implements Interrupt {
        public InterruptImpl(Handler handler, Arena arena) {
            final lua_Callbacks.interrupt.Function f = (L, gc) ->
                    handler.interrupt(new LuaStateImpl(L), gc);
            this(lua_Callbacks.interrupt.allocate(f, arena));
        }
    }

    @Override
    public void interrupt(@Nullable Interrupt handler) {
        final MemorySegment handle = handler != null
                ? ((InterruptImpl) handler).handle
                : MemorySegment.NULL;
        lua_Callbacks.interrupt(callbacks, handle);
    }

    @Override
    public void interrupt(MemorySegment functionAddress) {
        lua_Callbacks.interrupt(callbacks, functionAddress);
    }

    record UserAtomImpl(MemorySegment handle) implements UserAtom {
        public UserAtomImpl(Handler handler, Arena arena) {
            final lua_Callbacks.useratom.Function f = (str, len) -> {
                byte[] raw = str
                        .reinterpret(len)
                        .toArray(ValueLayout.JAVA_BYTE);
                return handler.userAtom(
                        new String(raw, StandardCharsets.UTF_8)
                );
            };
            this(lua_Callbacks.useratom.allocate(f, arena));
        }
    }

    @Override
    public void userAtom(@Nullable UserAtom handler) {
        final MemorySegment handle = handler != null
                ? ((UserAtomImpl) handler).handle
                : MemorySegment.NULL;
        lua_Callbacks.useratom(callbacks, handle);
    }

    @Override
    public void userAtom(MemorySegment functionAddress) {
        lua_Callbacks.useratom(callbacks, functionAddress);
    }

    record OnAllocateImpl(MemorySegment handle) implements OnAllocate {
        public OnAllocateImpl(Handler handler, Arena arena) {
            final lua_Callbacks.onallocate.Function f = (L, oldSize, newSize) ->
                    handler.onAllocate(new LuaStateImpl(L), oldSize, newSize);
            this(lua_Callbacks.onallocate.allocate(f, arena));
        }
    }

    @Override
    public void onAllocate(@Nullable OnAllocate handler) {
        final MemorySegment handle = handler != null
                ? ((OnAllocateImpl) handler).handle
                : MemorySegment.NULL;
        lua_Callbacks.onallocate(callbacks, handle);
    }

    @Override
    public void onAllocate(MemorySegment functionAddress) {
        lua_Callbacks.onallocate(callbacks, functionAddress);
    }
}
