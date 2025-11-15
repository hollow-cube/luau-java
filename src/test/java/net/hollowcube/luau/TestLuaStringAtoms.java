package net.hollowcube.luau;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;

import static org.junit.jupiter.api.Assertions.*;

@LuaStateParam
class TestLuaStringAtoms {

    @Test
    void noCallbackAlwaysNon(LuaState state) {
        state.pushString("hello, world!");
        assertEquals(LuaState.NO_ATOM, state.toStringAtomRaw(-1));
        var str = assertInstanceOf(LuaString.Str.class, state.toStringAtom(-1));
        assertEquals("hello, world!", str.str());
        state.pop(1);
    }

    @Test
    void nonStringUnset(LuaState state) {
        state.pushNumber(1.5);
        assertEquals(LuaState.NO_ATOM, state.toStringAtomRaw(-1));
        assertNull(state.toStringAtom(-1));
        state.pop(1);
    }

    @Test
    void callbackSetsAtom(LuaState state, Arena arena) {
        state.callbacks().userAtom(LuaCallbacks.UserAtom.allocate((str) -> switch (str) {
            case "one" -> 1;
            case "two" -> 2;
            default -> LuaState.NO_ATOM;
        }, arena));

        state.pushString("one");
        assertEquals(1, state.toStringAtomRaw(-1));
        var atom = assertInstanceOf(LuaString.Atom.class, state.toStringAtom(-1));
        assertEquals(1, atom.atom());
        state.pop(1);

        state.pushString("two");
        assertEquals(2, state.toStringAtomRaw(-1));
        atom = assertInstanceOf(LuaString.Atom.class, state.toStringAtom(-1));
        assertEquals(2, atom.atom());
        state.pop(1);

        state.pushString("three");
        assertEquals(LuaState.NO_ATOM, state.toStringAtomRaw(-1));
        var str = assertInstanceOf(LuaString.Str.class, state.toStringAtom(-1));
        assertEquals("three", str.str());
        state.pop(1);
    }

    // TODO: namecall atom test

}
