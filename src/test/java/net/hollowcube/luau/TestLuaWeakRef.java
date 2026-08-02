package net.hollowcube.luau;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/// Weak references live in their own registry, which the collector clears, so the ids they
/// hand out are not interchangeable with [LuaState#ref(int)] ids.
///
/// The backing table is only created when `LuauGcTraceUdata` is on, which
/// `luaW_setflagsdefault` handles before any state exists - see LuaStateImpl.
@LuaStateParam
class TestLuaWeakRef {

    @Test
    void collectedOnceUnreachable(LuaState state) {
        state.newTable();
        final int ref = state.weakRef(-1);
        state.pop(1);

        assertEquals(LuaType.TABLE, state.getWeakRef(ref));
        state.pop(1);

        state.gc(LuaGcOp.COLLECT, 0);

        assertEquals(LuaType.NIL, state.getWeakRef(ref));
        state.pop(1);
    }

    @Test
    void survivesWhileReachable(LuaState state) {
        state.newTable();
        state.pushValue(-1);
        state.setGlobal("keep");
        final int ref = state.weakRef(-1);
        state.pop(1);

        state.gc(LuaGcOp.COLLECT, 0);

        assertEquals(LuaType.TABLE, state.getWeakRef(ref));
        state.pop(1);
    }

    /// A strong ref keeps its referent alive across the same collection, which is the whole
    /// reason to reach for a weak one.
    @Test
    void strongRefKeepsAlive(LuaState state) {
        state.newTable();
        final int strong = state.ref(-1);
        final int weak = state.weakRef(-1);
        state.pop(1);

        state.gc(LuaGcOp.COLLECT, 0);
        assertEquals(LuaType.TABLE, state.getWeakRef(weak));
        state.pop(1);

        state.unref(strong);
        state.gc(LuaGcOp.COLLECT, 0);
        assertEquals(LuaType.NIL, state.getWeakRef(weak));
        state.pop(1);
    }

    /// Releasing a ref recycles its slot into a free list rather than clearing it, so a
    /// released id reads back as the internal link value - a number, not nil. Ids must not
    /// be used after release.
    @Test
    void unrefRecyclesTheSlot(LuaState state) {
        state.newTable();
        state.pushValue(-1);
        state.setGlobal("keep");
        final int ref = state.weakRef(-1);
        state.pop(1);

        state.weakUnref(ref);

        assertNotEquals(LuaType.TABLE, state.getWeakRef(ref));
        state.pop(1);
    }

    /// Ids are allocated per registry, so the same number means different things in each.
    /// Passing one to the wrong accessor is silently wrong rather than an error.
    @Test
    void registriesAreIndependent(LuaState state) {
        state.pushString("strong");
        final int strong = state.ref(-1);
        state.pop(1);

        state.pushString("weak");
        state.pushValue(-1);
        state.setGlobal("keep");
        final int weak = state.weakRef(-1);
        state.pop(1);

        assertEquals(strong, weak);

        assertEquals(LuaType.STRING, state.getRef(strong));
        assertEquals("strong", state.toString(-1));
        state.pop(1);

        assertEquals(LuaType.STRING, state.getWeakRef(weak));
        assertEquals("weak", state.toString(-1));
        state.pop(1);
    }
}
