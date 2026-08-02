package net.hollowcube.luau;

import static net.hollowcube.luau.TestHelpers.eval;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.ref.WeakReference;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The lifetime of the Lua objects Java hands to a state, and how much of it survives a
/// close.
///
/// Every Java object given to Lua is held by a JNI global reference for as long as the Lua
/// side owns it, which the userdata destructor releases. So "the destructor ran" is
/// observable from Java as "the object became collectible", and that is what the tests here
/// use to tell whether a value was really let go of.
///
/// A few neighbouring cases are deliberately absent because they are undefined rather than
/// merely wrong, and the native library is built with api_check enabled, so they abort the
/// process instead of throwing: closing a state twice, using a state after closing it, and
/// calling close() on a thread (lua_close redirects to the main thread and destroys the
/// whole state under the parent's feet).
@LuaStateParam
class TestLuaLifecycle {

    /// Allocates a value only Lua holds on to, so that the returned reference is cleared as
    /// soon as its userdata destructor runs. The value is created here rather than by the
    /// caller so that no frame slot keeps it alive.
    private static WeakReference<Object> pushOwnedUserData(LuaState state) {
        final Object value = new Object();
        state.newUserData(value);
        return new WeakReference<>(value);
    }

    private static WeakReference<Object> pushOwnedUserDataTagged(LuaState state, int tag) {
        final Object value = new Object();
        state.newUserDataTagged(value, tag);
        return new WeakReference<>(value);
    }

    private static WeakReference<Object> setOwnedThreadData(LuaState state) {
        final Object data = new Object();
        state.setThreadData(data);
        return new WeakReference<>(data);
    }

    private static void assertCollected(WeakReference<?> ref) {
        for (int i = 0; i < 200; i++) {
            if (ref.refersTo(null)) return;
            System.gc();
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
        fail("value was still reachable, its destructor did not run");
    }

    @Nested
    class Destructors {

        @Test
        void untaggedUserDataIsReleasedOnCollect(LuaState state) {
            final WeakReference<Object> ref = pushOwnedUserData(state);
            state.pop(1);

            state.gc(LuaGcOp.COLLECT, 0);

            assertCollected(ref);
        }

        @Test
        void taggedUserDataIsReleasedOnCollect(LuaState state) {
            final WeakReference<Object> ref = pushOwnedUserDataTagged(state, 5);
            state.pop(1);

            state.gc(LuaGcOp.COLLECT, 0);

            assertCollected(ref);
        }

        /// The control for the two above: while Lua still holds the userdata the JNI global
        /// reference keeps the Java object alive no matter what either collector does.
        @Test
        void reachableUserDataIsNotReleased(LuaState state) {
            final WeakReference<Object> ref = pushOwnedUserData(state);
            state.setGlobal("held");

            state.gc(LuaGcOp.COLLECT, 0);
            System.gc();

            assertFalse(ref.refersTo(null));
            state.getGlobal("held");
            assertNotNull(state.toUserData(-1));
        }

        /// Userdata reachable only from a thread's stack dies with the thread, which is
        /// itself an ordinary collectible object once nothing refers to it.
        @Test
        void aCollectedThreadReleasesItsStack(LuaState state) {
            final LuaState thread = state.newThread();
            final WeakReference<Object> ref = pushOwnedUserData(thread);

            state.gc(LuaGcOp.COLLECT, 0);
            System.gc();
            assertFalse(ref.refersTo(null), "the thread is still on the parent's stack");

            state.pop(1); // drop the only reference to the thread
            state.gc(LuaGcOp.COLLECT, 0);

            assertCollected(ref);
            // `thread` is now dangling, and must not be touched again
        }

        @Test
        void closeReleasesLiveUserData() {
            final WeakReference<Object> ref;
            try (LuaState state = LuaState.newState()) {
                ref = pushOwnedUserData(state);
                state.setGlobal("held");
            }

            assertCollected(ref);
        }

        @Test
        void closeReleasesThreadData() {
            final WeakReference<Object> ref;
            try (LuaState state = LuaState.newState()) {
                ref = setOwnedThreadData(state);
                assertNotNull(state.getThreadData());
            }

            assertCollected(ref);
        }

        /// Thread data is a plain reference rather than a userdata, so it is the setter which
        /// has to drop the previous one.
        @Test
        void replacingThreadDataReleasesThePrevious(LuaState state) {
            final WeakReference<Object> ref = setOwnedThreadData(state);
            assertFalse(ref.refersTo(null));

            state.setThreadData(new Object());

            assertCollected(ref);
        }
    }

    @Nested
    class Close {

        /// The state owns its threads, so closing it tears their stacks down too. This is
        /// also the regression case for the Java callbacks being freed before the threads
        /// that need them are closed.
        @Test
        void closeWithLiveThreadsReleasesTheirValues() {
            final WeakReference<Object> ref;
            final LuaState state = LuaState.newState();
            state.openLibs();

            final LuaState thread = state.newThread();
            eval(thread, "local x = 1 + 2");
            ref = pushOwnedUserData(thread);

            assertDoesNotThrow(state::close);
            assertCollected(ref);
        }

        /// Threads are collectible objects rather than resources, so there is nothing to
        /// close on one; the parent close is what ends their lifetime.
        @Test
        void threadsOutliveEveryReferenceUntilTheParentCloses() {
            final LuaState state = LuaState.newState();
            state.openLibs();

            final LuaState thread = state.newThread();
            state.pushValue(-1);
            state.setGlobal("keptAlive"); // rooted, so the GC cannot take it
            state.pop(1);

            state.gc(LuaGcOp.COLLECT, 0);

            eval(thread, "local x = 1 + 2");
            assertEquals(LuaCoStatus.FINISHED, state.costatus(thread));

            state.close();
        }
    }

    @Nested
    class Gc {

        @Test
        void collectorIsRunningByDefault(LuaState state) {
            assertEquals(1, state.gc(LuaGcOp.IS_RUNNING, 0));
        }

        @Test
        void stopAndRestart(LuaState state) {
            state.gc(LuaGcOp.STOP, 0);
            assertEquals(0, state.gc(LuaGcOp.IS_RUNNING, 0));

            state.gc(LuaGcOp.RESTART, 0);
            assertEquals(1, state.gc(LuaGcOp.IS_RUNNING, 0));
        }

        @Test
        void countReportsTheHeapSize(LuaState state) {
            state.openLibs();

            final int kb = state.gc(LuaGcOp.COUNT, 0);
            final int remainder = state.gc(LuaGcOp.COUNTB, 0);

            assertTrue(kb > 0, "the libraries alone are more than a kilobyte");
            assertTrue(remainder >= 0 && remainder < 1024, "remainder was " + remainder);
            assertEquals(state.totalBytes(0), kb * 1024L + remainder);
        }

        /// Collecting is observable in the heap size, which is the other half of the
        /// destructor story above.
        @Test
        void collectShrinksTheHeap(LuaState state) {
            state.openLibs();
            eval(state, """
                    local held = {}
                    for i = 1, 2000 do
                        held[i] = { value = i }
                    end
                    """);

            final long afterAllocation = state.totalBytes(0);
            state.gc(LuaGcOp.COLLECT, 0);

            assertTrue(state.totalBytes(0) < afterAllocation,
                    "expected the garbage to be freed, heap went from " + afterAllocation
                            + " to " + state.totalBytes(0));
        }
    }
}
