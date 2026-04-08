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

    sealed interface Preempt permits LuaCallbacksImpl.PreemptImpl {

        @FunctionalInterface
        interface Handler {
            /**
             * This callback works in conjunction with the interrupt() callback (with a robust implementation
             * supplied by default unless explicitly overridden) to support preempting running Luau scripts
             * by either yielding them or throwing an error. Note that usual yieldability rules apply - Luau
             * does not support yielding across metamethod or C-call boundaries, and an attempt to do so
             * will result in an error.
             *
             * Note that just like the interrupt() callback, this function is extremely restricted in how
             * it can interact with Lua state. You should never modify the Lua stack or any other state
             * from this function, and you should not attempt to either yield or error without using
             * the return/throw API provided for that purpose.
             *
             * @param state the LuaState that triggered this call
             * @param gc the gc operation flag
             * @return true to preempt with a yield, false otherwise
             * @throws LuaError throws a LuaError to preempt with an error
             */
            boolean preempt(LuaState state, int gc) throws LuaError;
        }

        static Preempt allocate(Preempt.Handler handler, Arena arena) {
            return new LuaCallbacksImpl.PreemptImpl(handler, arena);
        }
    }

    void preempt(@Nullable LuaCallbacks.Preempt handler);

    /**
     * The preempt() callback gets called at safepoints (loop back edges, call/ret, gc) if set AND
     * the interrupt() callback is set to luaW_interrupt_preempt_handler. Note that the current
     * implementation of this method overwrites the interrupt() callback luaW_interrupt_preempt_handler.
     * You may use your own interrupt() callback, but to do so you must set it after calling preempt().
     * Note that if at all possible you should avoid using your own interrupt() handler, as it is
     * subtle and highly specific in what it is and is not allowed to do with the Lua state. Furthermore,
     * it must generally be native code due to how Lua propagates errors. If you are using preemption,
     * virtually all of what interrupt() can do is already exposed via the preemption yield/error API.
     */
    void preempt(MemorySegment functionAddress);

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
