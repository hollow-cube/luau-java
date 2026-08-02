package net.hollowcube.luau;

import static net.hollowcube.luau.TestHelpers.eval;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import org.junit.jupiter.api.Test;

/// Luau's `integer` is a 64 bit integer type distinct from `number`, written `123i` in
/// source and constructed at runtime with `integer.create`.
@LuaStateParam
class TestLuaInteger64 {

    @Test
    void pushAndRead(LuaState state) {
        state.pushInteger64(Long.MAX_VALUE);

        assertEquals(LuaType.INTEGER, state.type(-1));
        assertTrue(state.isInteger64(-1));
        assertEquals(Long.MAX_VALUE, state.toInteger64(-1));
        state.pop(1);
    }

    /// The VM does not coerce between `integer` and `number` in either direction, which is
    /// worth pinning down because the number accessors fail silently rather than throwing.
    @Test
    void isNotANumber(LuaState state) {
        state.pushInteger64(7);

        assertFalse(state.isNumber(-1));
        assertEquals(0, state.toNumber(-1));
        assertEquals(0, state.toInteger(-1));
        assertNull(state.toNumberOrNull(-1));

        state.pushNumber(7);
        assertFalse(state.isInteger64(-1));
        assertEquals(0, state.toInteger64(-1));
        assertNull(state.toInteger64OrNull(-1));

        state.pop(2);
    }

    @Test
    void readsIntegerFromScript(LuaState state) {
        state.openLibs();
        eval(state, "return 0xABABi", 1);

        assertTrue(state.isInteger64(-1));
        assertEquals(43947, state.toInteger64(-1));
        state.pop(1);
    }

    @Test
    void passesIntegerToScript(LuaState state) {
        state.openLibs();
        state.pushInteger64(-1_000_000_000_000L);
        state.setGlobal("value");

        eval(state, "return type(value), value == -1000000000000i", 2);

        assertEquals("integer", state.toString(-2));
        assertTrue(state.toBoolean(-1));
        state.pop(2);
    }

    @Test
    void checkAndOpt(LuaState state, Arena arena) {
        state.openLibs();
        state.pushFunction(LuaFunc.wrap(s -> {
            s.pushInteger64(s.checkInteger64(1) + s.optInteger64(2, 100));
            return 1;
        }, "add", arena));
        state.setGlobal("add");

        eval(state, "return add(5i)", 1);
        assertEquals(105, state.toInteger64(-1));
        state.pop(1);

        eval(state, "return add(5i, 6i)", 1);
        assertEquals(11, state.toInteger64(-1));
        state.pop(1);
    }

    @Test
    void checkRejectsNumber(LuaState state, Arena arena) {
        state.openLibs();
        state.pushFunction(LuaFunc.wrap(s -> {
            s.pushInteger64(s.checkInteger64(1));
            return 1;
        }, "identity", arena));
        state.setGlobal("identity");

        final LuaError error = assertThrows(LuaError.class, () -> eval(state, "return identity(5)", 1));
        assertEquals("invalid argument #1 to 'identity' (integer expected, got number)", error.getMessage());
    }
}
