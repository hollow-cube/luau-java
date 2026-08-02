package net.hollowcube.luau.callbacks;

import net.hollowcube.luau.LuaCallbacks;
import net.hollowcube.luau.LuaGcOp;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaStateParam;
import net.hollowcube.luau.LuaString;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static net.hollowcube.luau.TestHelpers.eval;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// The lua_Callbacks handlers other than preempt (see preempt/TestLuaPreempt).
///
/// The upcall stubs must outlive the state, so everything here allocates out of an
/// automatic arena rather than the injected confined one, which is closed alongside the
/// state at the end of the test.
@LuaStateParam
class TestLuaCallbacks {

    final Arena arena = Arena.ofAuto();

    private static final String BUSY_LOOP = """
        local i = 0
        while i < 1000 do
            i = i + 1
        end
        return i
        """;

    //region interrupt

    @Test
    void interruptFiresAtSafepoints(LuaState state) {
        final AtomicInteger calls = new AtomicInteger();
        final List<Integer> gcArgs = new ArrayList<>();
        state.callbacks().interrupt(LuaCallbacks.Interrupt.allocate((s, gc) -> {
            calls.incrementAndGet();
            gcArgs.add(gc);
        }, arena));

        eval(state, BUSY_LOOP, 1);

        assertEquals(1000, state.toInteger(-1));
        assertTrue(calls.get() > 0, "interrupt never fired");
        // -1 is "not a gc step"; anything else is a gc step size and never negative.
        assertTrue(
                gcArgs.stream().allMatch(gc -> gc == -1 || gc >= 0),
                "unexpected gc argument: " + gcArgs
        );
        assertTrue(gcArgs.contains(-1), "expected at least one non-gc interrupt");
    }

    @Test
    void interruptReceivesTheRunningState(LuaState state) {
        final List<LuaState> states = new ArrayList<>();
        state.callbacks().interrupt(LuaCallbacks.Interrupt.allocate(
                (s, gc) -> {
                    if (states.isEmpty()) states.add(s);
                },
                arena
        ));

        eval(state, BUSY_LOOP, 1);

        assertEquals(1, states.size());
        assertEquals(state.L().address(), states.getFirst().L().address());
    }

    @Test
    void interruptCanBeUnset(LuaState state) {
        final AtomicInteger calls = new AtomicInteger();
        state.callbacks().interrupt(LuaCallbacks.Interrupt.allocate(
                (s, gc) -> calls.incrementAndGet(),
                arena
        ));

        eval(state, BUSY_LOOP, 1);
        state.pop(1);
        final int before = calls.get();
        assertTrue(before > 0, "interrupt never fired");

        state.callbacks().interrupt((LuaCallbacks.Interrupt) null);
        eval(state, BUSY_LOOP, 1);

        assertEquals(before, calls.get());
    }

    @Test
    void interruptCanBeUnsetThroughTheSegmentOverload(LuaState state) {
        final AtomicInteger calls = new AtomicInteger();
        state.callbacks().interrupt(LuaCallbacks.Interrupt.allocate(
                (s, gc) -> calls.incrementAndGet(),
                arena
        ));

        eval(state, BUSY_LOOP, 1);
        state.pop(1);
        final int before = calls.get();

        state.callbacks().interrupt(MemorySegment.NULL);
        eval(state, BUSY_LOOP, 1);

        assertEquals(before, calls.get());
    }

    @Test
    void interruptIsReplacedByTheNewHandler(LuaState state) {
        final AtomicInteger first = new AtomicInteger();
        final AtomicInteger second = new AtomicInteger();
        state.callbacks().interrupt(LuaCallbacks.Interrupt.allocate(
                (s, gc) -> first.incrementAndGet(),
                arena
        ));
        state.callbacks().interrupt(LuaCallbacks.Interrupt.allocate(
                (s, gc) -> second.incrementAndGet(),
                arena
        ));

        eval(state, BUSY_LOOP, 1);

        assertEquals(0, first.get());
        assertTrue(second.get() > 0, "the replacement handler never fired");
    }

    //endregion

    //region onAllocate

    /// The handler runs inside the allocator, so it must not touch the Lua state at all.
    @Test
    void onAllocateSeesGrowingAllocations(LuaState state) {
        final List<long[]> sizes = new ArrayList<>();
        state.callbacks().onAllocate(LuaCallbacks.OnAllocate.allocate(
                (s, oldSize, newSize) -> sizes.add(new long[] { oldSize, newSize }),
                arena
        ));

        eval(state, """
            local t = {}
            for i = 1, 100 do
                t[i] = "value " .. i
            end
            return #t
            """, 1);

        assertEquals(100, state.toInteger(-1));
        assertFalse(sizes.isEmpty(), "onAllocate never fired");
        assertTrue(
                sizes.stream().anyMatch(s -> s[0] == 0 && s[1] > 0),
                "expected a fresh allocation (oldSize 0)"
        );
        assertTrue(
                sizes.stream().allMatch(s -> s[0] >= 0 && s[1] >= 0),
                "sizes should never be negative"
        );
    }

    /// Growing a table's array part goes through the reallocating path, which is the only
    /// one that reports a non zero old size.
    @Test
    void onAllocateReportsReallocations(LuaState state) {
        final List<long[]> sizes = new ArrayList<>();
        state.callbacks().onAllocate(LuaCallbacks.OnAllocate.allocate(
                (s, oldSize, newSize) -> sizes.add(new long[] { oldSize, newSize }),
                arena
        ));

        eval(state, """
            local t = {}
            for i = 1, 2000 do
                t[i] = i
            end
            return #t
            """, 1);

        assertEquals(2000, state.toInteger(-1));
        // The array part doubles, so every regrowth is reported as old -> old * 2.
        assertTrue(
                sizes.stream().anyMatch(s -> s[0] >= 16 && s[1] == s[0] * 2),
                "expected a doubling reallocation, got " + describe(sizes)
        );
    }

    private static String describe(List<long[]> sizes) {
        return sizes.stream()
                .map(s -> s[0] + "->" + s[1])
                .toList()
                .toString();
    }

    @Test
    void onAllocateCanBeUnset(LuaState state) {
        final AtomicInteger calls = new AtomicInteger();
        state.callbacks().onAllocate(LuaCallbacks.OnAllocate.allocate(
                (s, oldSize, newSize) -> calls.incrementAndGet(),
                arena
        ));

        eval(state, "local t = { 1, 2, 3 }");
        final int before = calls.get();
        assertTrue(before > 0, "onAllocate never fired");

        state.callbacks().onAllocate((LuaCallbacks.OnAllocate) null);
        eval(state, "local t = { 1, 2, 3 }");

        assertEquals(before, calls.get());
    }

    @Test
    void onAllocateCanBeUnsetThroughTheSegmentOverload(LuaState state) {
        final AtomicInteger calls = new AtomicInteger();
        state.callbacks().onAllocate(LuaCallbacks.OnAllocate.allocate(
                (s, oldSize, newSize) -> calls.incrementAndGet(),
                arena
        ));

        eval(state, "local t = { 1, 2, 3 }");
        final int before = calls.get();

        state.callbacks().onAllocate(MemorySegment.NULL);
        eval(state, "local t = { 1, 2, 3 }");

        assertEquals(before, calls.get());
    }

    //endregion

    //region userAtom

    /// Atoms are resolved lazily: the callback is not asked until something actually reads
    /// the atom off the string.
    @Test
    void userAtomIsAskedWhenTheAtomIsRead(LuaState state) {
        final List<String> seen = new ArrayList<>();
        state.callbacks().userAtom(LuaCallbacks.UserAtom.allocate((s, str) -> {
            seen.add(str);
            return (short) str.length();
        }, arena));

        state.pushString("hello");
        assertEquals(List.of(), seen, "the atom should not be resolved eagerly");

        assertEquals(5, state.toStringAtomRaw(-1));
        assertEquals(List.of("hello"), seen);
    }

    /// The atom is kept on the interned string, so it is only ever resolved once.
    @Test
    void userAtomIsOnlyAskedOncePerString(LuaState state) {
        final List<String> seen = new ArrayList<>();
        state.callbacks().userAtom(LuaCallbacks.UserAtom.allocate((s, str) -> {
            seen.add(str);
            return 1;
        }, arena));

        state.pushString("repeated");
        assertEquals(1, state.toStringAtomRaw(-1));
        state.pushString("repeated");
        assertEquals(1, state.toStringAtomRaw(-1));

        assertEquals(List.of("repeated"), seen);
    }

    @Test
    void userAtomCanBeUnset(LuaState state) {
        state.callbacks().userAtom(LuaCallbacks.UserAtom.allocate((s, str) -> 7, arena));
        state.pushString("atomised");
        assertEquals(7, state.toStringAtomRaw(-1));

        state.callbacks().userAtom((LuaCallbacks.UserAtom) null);
        state.pushString("plain");

        assertEquals(LuaState.NO_ATOM, state.toStringAtomRaw(-1));
        assertEquals("plain", ((LuaString.Str) state.toStringAtom(-1)).str());
    }

    @Test
    void userAtomCanBeUnsetThroughTheSegmentOverload(LuaState state) {
        state.callbacks().userAtom(LuaCallbacks.UserAtom.allocate((s, str) -> 7, arena));
        state.pushString("atomised");
        assertEquals(7, state.toStringAtomRaw(-1));

        state.callbacks().userAtom(MemorySegment.NULL);
        state.pushString("plain segment");

        assertEquals(LuaState.NO_ATOM, state.toStringAtomRaw(-1));
    }

    //endregion

    //region userThread

    private record ThreadEvent(boolean creation, @Nullable Long parent, long thread) {}

    private static List<ThreadEvent> install(LuaState state, List<ThreadEvent> events) {
        state.callbacks().userThread((parent, thread) -> events.add(new ThreadEvent(
                parent != null,
                parent != null ? parent.L().address() : null,
                thread.L().address()
        )));
        return events;
    }

    @Test
    void userThreadFiresOnCreation(LuaState state) {
        final List<ThreadEvent> events = install(state, new ArrayList<>());

        final LuaState thread = state.newThread();

        assertEquals(1, events.size());
        final ThreadEvent event = events.getFirst();
        assertTrue(event.creation(), "expected a creation event");
        assertEquals(state.L().address(), event.parent());
        assertEquals(thread.L().address(), event.thread());
    }

    /// A thread created from Lua reports the thread it was created on as its parent.
    @Test
    void userThreadFiresForThreadsCreatedFromLua(LuaState state) {
        state.openLibs();
        final List<ThreadEvent> events = install(state, new ArrayList<>());

        eval(state, """
            local co = coroutine.create(function() end)
            return co
            """, 1);

        assertEquals(1, events.size());
        assertTrue(events.getFirst().creation());
        assertEquals(state.L().address(), events.getFirst().parent());
    }

    /// Collecting an unreachable thread reports a destruction, which is the same path the
    /// binding uses to drop its own references to the thread.
    @Test
    void userThreadFiresOnCollection(LuaState state) {
        final List<ThreadEvent> events = install(state, new ArrayList<>());

        final long created = state.newThread().L().address();
        state.pop(1); // newThread leaves the thread on the stack
        state.gc(LuaGcOp.COLLECT, 0);

        assertTrue(
                events.stream().anyMatch(e -> !e.creation() && e.thread() == created),
                "expected a destruction event for the collected thread: " + events
        );
        assertTrue(
                events.stream().filter(e -> !e.creation()).allMatch(e -> e.parent() == null),
                "destruction events must not report a parent"
        );
    }

    @Test
    void userThreadCanBeUnset(LuaState state) {
        final List<ThreadEvent> events = install(state, new ArrayList<>());

        state.newThread();
        state.pop(1);
        assertEquals(1, events.size());

        state.callbacks().userThread(null);
        state.newThread();
        state.pop(1);

        assertEquals(1, events.size());
    }

    /// Threads which are still alive when the state is closed are destroyed as part of the
    /// close, and the handler is still reachable at that point.
    ///
    /// @see net.hollowcube.luau.TestLuaState#regressionCloseWithThreadsDestroyingCallbacksEarly
    @Test
    void userThreadFiresWhileClosingTheState() {
        final List<ThreadEvent> events = new ArrayList<>();

        final LuaState state = LuaState.newState();
        state.openLibs();
        install(state, events);

        final LuaState thread = state.newThread();
        eval(thread, "local x = 1 + 2");
        final long created = thread.L().address();

        assertDoesNotThrow(state::close);

        assertTrue(
                events.stream().anyMatch(e -> !e.creation() && e.thread() == created),
                "the live thread was not reported as destroyed: " + events
        );
    }

    @Test
    void userThreadHandlerIsInvokedForNestedThreads(LuaState state) {
        final List<ThreadEvent> events = install(state, new ArrayList<>());

        final LuaState child = state.newThread();
        final LuaState grandChild = child.newThread();

        assertEquals(2, events.size());
        assertEquals(state.L().address(), events.getFirst().parent());
        assertEquals(child.L().address(), events.get(1).parent());
        assertEquals(grandChild.L().address(), events.get(1).thread());
    }

    //endregion
}
