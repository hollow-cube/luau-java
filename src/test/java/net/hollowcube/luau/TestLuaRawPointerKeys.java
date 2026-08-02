package net.hollowcube.luau;

import static net.hollowcube.luau.TestHelpers.eval;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/// Raw table access keyed by a tagged light userdata, which is how you build a registry
/// keyed by a native address without interning a string for every lookup.
@LuaStateParam
class TestLuaRawPointerKeys {

    @Test
    void roundTrips(LuaState state) {
        state.newTable();
        state.pushString("value");
        state.rawSetP(-2, 0xCAFE, 0);

        assertEquals(LuaType.STRING, state.rawGetP(-1, 0xCAFE, 0));
        assertEquals("value", state.toString(-1));
        state.pop(2);
    }

    @Test
    void missingKeyIsNil(LuaState state) {
        state.newTable();

        assertEquals(LuaType.NIL, state.rawGetP(-1, 0xCAFE, 0));
        state.pop(2);
    }

    /// The tag is part of the key, so the same address under two tags is two entries.
    @Test
    void tagIsPartOfTheKey(LuaState state) {
        state.newTable();
        state.pushString("first");
        state.rawSetP(-2, 0xCAFE, 1);
        state.pushString("second");
        state.rawSetP(-2, 0xCAFE, 2);

        assertEquals(LuaType.STRING, state.rawGetP(-1, 0xCAFE, 1));
        assertEquals("first", state.toString(-1));
        state.pop(1);

        assertEquals(LuaType.STRING, state.rawGetP(-1, 0xCAFE, 2));
        assertEquals("second", state.toString(-1));
        state.pop(2);
    }

    /// The key is a light userdata, so a script can read the entry back given the same
    /// pointer - it is not private to the host.
    @Test
    void visibleToScripts(LuaState state) {
        state.newTable();
        state.pushString("value");
        state.rawSetP(-2, 0xCAFE, 0);
        state.setGlobal("registry");
        state.pushLightUserData(0xCAFE);
        state.setGlobal("key");

        eval(state, "return registry[key]", 1);

        assertEquals("value", state.toString(-1));
        state.pop(1);
    }

    @Test
    void respectsReadOnly(LuaState state) {
        state.newTable();
        state.setReadOnly(-1, true);
        state.pushString("value");

        assertThrows(LuaError.class, () -> state.rawSetP(-2, 0xCAFE, 0));
    }
}
