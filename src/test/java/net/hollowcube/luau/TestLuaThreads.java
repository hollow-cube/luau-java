package net.hollowcube.luau;

import static net.hollowcube.luau.TestHelpers.eval;
import static net.hollowcube.luau.TestHelpers.load;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Threads (coroutines) created and driven from Java rather than through the coroutine
/// library. A thread shares the global state with its parent but has its own stack, so most
/// of what is checked here is which of the two stacks a given call touches.
@LuaStateParam
class TestLuaThreads {

    @Nested
    class Creation {

        @Test
        void newThreadIsPushedOntoTheParentStack(LuaState state) {
            final LuaState thread = state.newThread();

            assertEquals(1, state.top());
            assertTrue(state.isThread(-1));
            assertEquals(LuaType.THREAD, state.type(-1));
            assertEquals("thread", state.typeName(-1));
            assertEquals(thread, state.toThread(-1));
            assertEquals(0, thread.top(), "the new thread has its own, empty stack");
        }

        @Test
        void newThreadSharesGlobals(LuaState state) {
            state.pushString("shared");
            state.setGlobal("marker");

            final LuaState thread = state.newThread();
            thread.getGlobal("marker");
            assertEquals("shared", thread.toString(-1));

            thread.pushString("fromThread");
            thread.setGlobal("marker2");
            state.getGlobal("marker2");
            assertEquals("fromThread", state.toString(-1));
        }

        @Test
        void mainThreadIsShared(LuaState state) {
            final LuaState thread = state.newThread();

            assertEquals(state, state.mainThread());
            assertEquals(state, thread.mainThread());
            assertNotEquals(thread, thread.mainThread());
        }

        @Test
        void isThreadIsFalseForOtherValues(LuaState state) {
            state.pushString("not a thread");
            assertFalse(state.isThread(-1));
            assertNull(state.toThread(-1));

            state.newTable();
            assertFalse(state.isThread(-1));
            assertNull(state.toThread(-1));
        }

        /// lua_pushthread reports whether the thread it pushed is the main one.
        @Test
        void pushThreadReportsTheMainThread(LuaState state) {
            assertTrue(state.pushThread(state));
            assertTrue(state.isThread(-1));
            assertEquals(state, state.toThread(-1));

            final LuaState thread = state.newThread();
            assertFalse(thread.pushThread(thread));
            assertEquals(thread, thread.toThread(-1));
        }

        /// lua_pushthread can only push a thread onto its own stack, so pushing another
        /// thread has to move the value across to the receiver afterwards.
        @Test
        void pushThreadPushesOntoTheReceiver(LuaState state) {
            final LuaState thread = state.newThread();
            final int top = state.top();

            assertFalse(state.pushThread(thread), "the child is not the main thread");

            assertEquals(top + 1, state.top(), "the thread landed on the receiver");
            assertEquals(thread, state.toThread(-1));
            assertEquals(0, thread.top(), "and nothing was left on the pushed thread");
        }
    }

    @Nested
    class StackTransfer {

        @Test
        void xmoveTransfersValues(LuaState state) {
            final LuaState thread = state.newThread();

            state.pushString("a");
            state.pushInteger(7);
            final int top = state.top();
            state.xmove(thread, 2);

            assertEquals(top - 2, state.top(), "xmove pops from the source");
            assertEquals(2, thread.top());
            assertEquals("a", thread.toString(-2));
            assertEquals(7, thread.toInteger(-1));
        }

        @Test
        void xpushCopiesASingleValue(LuaState state) {
            final LuaState thread = state.newThread();

            state.newTable();
            state.pushString("value");
            state.setField(-2, "key");
            final int top = state.top();

            state.xpush(thread, -1);
            assertEquals(top, state.top(), "xpush leaves the source alone");
            assertEquals(1, thread.top());
            assertTrue(thread.isTable(-1));

            // the same table, not a copy
            thread.pushString("other");
            thread.setField(-2, "key2");
            assertEquals(LuaType.STRING, state.getField(-1, "key2"));
            assertEquals("other", state.toString(-1));
        }
    }

    @Nested
    class Resume {

        @Test
        void resumeRunsToCompletion(LuaState state) {
            final LuaState thread = state.newThread();
            load(thread, "return 1 + 2");

            assertEquals(LuaStatus.OK, thread.resume(null, 0));
            assertEquals(1, thread.top());
            assertEquals(3, thread.toInteger(-1));
        }

        @Test
        void resumeYieldsAndResumesWithValues(LuaState state) {
            state.openLibs();
            final LuaState thread = state.newThread();
            load(thread, """
                    local given = coroutine.yield("waiting")
                    return given + 1
                    """);

            assertEquals(LuaStatus.YIELD, thread.resume(null, 0));
            assertEquals("waiting", thread.toString(-1));
            assertEquals(LuaStatus.YIELD, thread.status());
            thread.pop(1);

            thread.pushInteger(41);
            assertEquals(LuaStatus.OK, thread.resume(null, 1));
            assertEquals(42, thread.toInteger(-1));
            assertEquals(LuaStatus.OK, thread.status());
        }

        @Test
        void resumeArgumentsBecomeTheChunkArguments(LuaState state) {
            final LuaState thread = state.newThread();
            load(thread, "return ...");

            thread.pushString("arg");
            assertEquals(LuaStatus.OK, thread.resume(null, 1));
            assertEquals("arg", thread.toString(-1));
        }

        @Test
        void errorInsideResumePropagates(LuaState state) {
            state.openLibs();
            final LuaState thread = state.newThread();
            load(thread, "error('boom')");

            final LuaError error = assertThrows(LuaError.class, () -> thread.resume(null, 0));
            // the chunkname:line prefix is stripped, it is carried by the stack trace instead
            assertEquals("boom", error.getMessage());
            assertEquals(LuaStatus.ERRRUN, thread.status());
        }

        /// A thread which ran to completion has an empty stack, which is what the VM uses to
        /// tell that there is nothing left to call.
        @Test
        void resumeOfADeadThreadFails(LuaState state) {
            final LuaState thread = state.newThread();
            load(thread, "local x = 1 + 2");
            assertEquals(LuaStatus.OK, thread.resume(null, 0));
            assertEquals(0, thread.top());

            final LuaError error = assertThrows(LuaError.class, () -> thread.resume(null, 0));
            assertEquals("cannot resume dead coroutine", error.getMessage());
        }

        /// After an error the thread is neither suspended nor finished, and resume_start
        /// rejects it before it ever runs.
        @Test
        void resumeOfAnErroredThreadFails(LuaState state) {
            state.openLibs();
            final LuaState thread = state.newThread();
            load(thread, "error('boom')");
            assertThrows(LuaError.class, () -> thread.resume(null, 0));

            final LuaError error = assertThrows(LuaError.class, () -> thread.resume(null, 0));
            assertEquals("cannot resume non-suspended coroutine", error.getMessage());
        }

        /// The `from` argument only donates the C call depth, so passing it changes nothing
        /// observable for a shallow call like this one.
        @Test
        void resumeAcceptsAFromThread(LuaState state) {
            final LuaState thread = state.newThread();
            load(thread, "return 'ok'");

            assertEquals(LuaStatus.OK, thread.resume(state, 0));
            assertEquals("ok", thread.toString(-1));
        }
    }

    @Nested
    class Status {

        @Test
        void statusOfAFreshThreadIsOk(LuaState state) {
            final LuaState thread = state.newThread();

            assertEquals(LuaStatus.OK, thread.status());
            assertEquals(LuaStatus.OK, state.status());
        }

        /// costatus is asked from the perspective of the caller, so the caller itself is the
        /// one that is running.
        @Test
        void coStatusOfTheRunningThread(LuaState state) {
            assertEquals(LuaCoStatus.RUNNING, state.costatus(state));
        }

        /// An empty thread and a completed thread are indistinguishable - both have nothing
        /// left on the stack to call.
        @Test
        void coStatusFollowsTheThreadLifecycle(LuaState state) {
            final LuaState thread = state.newThread();
            assertEquals(LuaCoStatus.FINISHED, state.costatus(thread));

            load(thread, "local x = 1 + 2");
            assertEquals(LuaCoStatus.SUSPENDED, state.costatus(thread));

            assertEquals(LuaStatus.OK, thread.resume(null, 0));
            assertEquals(LuaCoStatus.FINISHED, state.costatus(thread));
        }

        @Test
        void coStatusOfAYieldedThreadIsSuspended(LuaState state) {
            state.openLibs();
            final LuaState thread = state.newThread();
            load(thread, "coroutine.yield()");

            assertEquals(LuaStatus.YIELD, thread.resume(null, 0));
            assertEquals(LuaCoStatus.SUSPENDED, state.costatus(thread));
        }

        @Test
        void coStatusOfAnErroredThreadIsError(LuaState state) {
            state.openLibs();
            final LuaState thread = state.newThread();
            load(thread, "error('boom')");
            assertThrows(LuaError.class, () -> thread.resume(null, 0));

            assertEquals(LuaCoStatus.ERROR, state.costatus(thread));
        }

        /// A thread which is part way through resuming another one is 'normal': not running,
        /// but not suspended either.
        @Test
        void coStatusOfAResumingThreadIsNormal(LuaState state, Arena arena) {
            state.openLibs();
            state.pushFunction(LuaFunc.wrap(s -> {
                s.pushString(s.costatus(s.toThread(1)).name());
                return 1;
            }, "costatus", arena));
            state.setGlobal("costatus");

            eval(state, """
                    local outer
                    outer = coroutine.create(function()
                        local inner = coroutine.create(function()
                            return costatus(outer)
                        end)
                        local _, status = coroutine.resume(inner)
                        return status
                    end)
                    local _, status = coroutine.resume(outer)
                    return status
                    """, 1);

            assertEquals("NORMAL", state.toString(-1));
        }
    }

    @Nested
    class Yieldable {

        /// nCcalls == baseCcalls == 0 on an idle state, so an untouched main thread reports
        /// as yieldable even though there is nothing there to yield.
        @Test
        void idleMainThreadReportsYieldable(LuaState state) {
            assertTrue(state.isYieldable());
        }

        @Test
        void notYieldableInsideACallOnTheMainThread(LuaState state, Arena arena) {
            state.pushFunction(LuaFunc.wrap(s -> {
                s.pushBoolean(s.isYieldable());
                return 1;
            }, "yieldable", arena));
            state.call(0, 1);

            assertFalse(state.toBoolean(-1));
        }

        @Test
        void yieldableInsideAResumedThread(LuaState state, Arena arena) {
            state.pushFunction(LuaFunc.wrap(s -> {
                s.pushBoolean(s.isYieldable());
                return 1;
            }, "yieldable", arena));
            state.setGlobal("yieldable");

            final LuaState thread = state.newThread();
            load(thread, "return yieldable()");

            assertEquals(LuaStatus.OK, thread.resume(null, 0));
            assertTrue(thread.toBoolean(-1));
        }
    }

    @Nested
    class Reset {

        @Test
        void freshThreadIsReset(LuaState state) {
            final LuaState thread = state.newThread();

            assertTrue(thread.isThreadReset());
        }

        @Test
        void anythingOnTheStackMakesItNonReset(LuaState state) {
            final LuaState thread = state.newThread();
            thread.pushString("value");

            assertFalse(thread.isThreadReset());

            thread.resetThread();
            assertTrue(thread.isThreadReset());
            assertEquals(0, thread.top());
        }

        /// Resetting is the way back from an error: the status is cleared along with the
        /// stack, so the thread can be loaded and resumed again.
        @Test
        void resetClearsAnError(LuaState state) {
            state.openLibs();
            final LuaState thread = state.newThread();
            load(thread, "error('boom')");
            assertThrows(LuaError.class, () -> thread.resume(null, 0));
            assertEquals(LuaStatus.ERRRUN, thread.status());
            assertFalse(thread.isThreadReset());

            thread.resetThread();

            assertTrue(thread.isThreadReset());
            assertEquals(LuaStatus.OK, thread.status());
            assertEquals(LuaCoStatus.FINISHED, state.costatus(thread));

            load(thread, "return 'reused'");
            assertEquals(LuaStatus.OK, thread.resume(null, 0));
            assertEquals("reused", thread.toString(-1));
        }
    }
}
