package net.hollowcube.luau;

import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

/// Wraps the lua_Callbacks api for configuring the VM behavior at runtime.
///
/// Since many of these functions are very hot, a lower level (eg direct MemorySegment)
/// api has been left in case you wish to use a native implementation.
///
/// For java implementations, the function handle allocation must be managed by the caller.
/// The Arena used to allocate the function must survive as long as the lua state. You can
/// use [Arena#global()] for a lifetime allocation.
///
/// Note: the debug methods are not implemented as debugging is not yet supported
///       as well as the panic function which may not occur as we do not allow
///       unprotected calls or longjmps across boundary.
///       Userdata has been reserved for potential future library usage for now.
public sealed interface LuaCallbacks permits LuaCallbacksImpl {
    sealed interface Interrupt permits LuaCallbacksImpl.InterruptImpl {
        @FunctionalInterface
        interface Handler {
            void interrupt(LuaState state, int gc);
        }

        static Interrupt allocate(Handler handler, Arena arena) {
            return new LuaCallbacksImpl.InterruptImpl(handler, arena);
        }
    }

    /// gets called at safepoints (loop back edges, call/ret, gc) if set
    void interrupt(@Nullable Interrupt handler);
    void interrupt(MemorySegment functionAddress);

    sealed interface UserAtom permits LuaCallbacksImpl.UserAtomImpl {
        @FunctionalInterface
        interface Handler {
            short userAtom(String string);
        }

        static UserAtom allocate(Handler handler, Arena arena) {
            return new LuaCallbacksImpl.UserAtomImpl(handler, arena);
        }

        //todo could add native utility function which just reads from a map or something
        // to return the atom if you know the entire list in advance. Would have to be set
        // globally however since we have no context.
    }

    /// gets called when a string is created; returned atom can be retrieved via tostringatom
    void userAtom(@Nullable UserAtom handler);
    void userAtom(MemorySegment functionAddress);

    sealed interface OnAllocate permits LuaCallbacksImpl.OnAllocateImpl {
        @FunctionalInterface
        interface Handler {
            void onAllocate(LuaState state, long oldSize, long newSize);
        }

        static OnAllocate allocate(Handler handler, Arena arena) {
            return new LuaCallbacksImpl.OnAllocateImpl(handler, arena);
        }
    }

    /// gets called when memory is allocated
    void onAllocate(@Nullable OnAllocate handler);
    void onAllocate(MemorySegment functionAddress);

    /// The userthread callback is proxied java side since luau-java uses the callback
    /// for cleaning up java references on destroyed threads. As such, it does not need
    /// to be allocated. The lifetime is managed by the LuaState.
    @FunctionalInterface
    interface UserThread {
        /// parent is present when creating a thread, null when destroying
        void userThread(@Nullable LuaState parent, LuaState state);
    }

    /// gets called when L is created (LP == parent) or destroyed (LP == NULL)
    void userThread(@Nullable UserThread handler);

}
