package net.hollowcube.luau;

import static net.hollowcube.luau.TestHelpers.eval;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

/// Luau stringifies anything without a `__tostring` as `type: 0xADDRESS`, running the
/// address through a per state key first. That key defaults to the identity function
/// upstream, so states created here install a random one instead.
@LuaStateParam
class TestLuaPointerMasking {

    /// A light userdata carries its payload as the pointer, which makes the encoding
    /// directly observable: unmasked, 12345 would stringify as 0x0000000000003039.
    @Test
    void addressesAreNotRaw(LuaState state) {
        state.pushLightUserData(12345);

        assertNotEquals("userdata: 0x0000000000003039", state.toStringRepr(-1));
        state.pop(1);
    }

    @Test
    void keyCanBePinned(LuaState state) {
        state.setPointerEncodeKey(0, 1, 0, 0);
        state.pushLightUserData(12345);

        // a=0, b=1, c=d=0 reduces the encoding to (0 * p) ^ (1 * p), which is p again.
        assertEquals("userdata: 0x0000000000003039", state.toStringRepr(-1));
        state.pop(1);
    }

    /// Two objects must never stringify alike, which is why the key has a fixed parity
    /// rather than being applied as given.
    @Test
    void distinctObjectsStayDistinct(LuaState state) {
        state.openLibs();
        eval(state, """
                local a, b = {}, {}
                return tostring(a) ~= tostring(b), tostring(a) == tostring(a)
                """, 2);

        assertEquals(true, state.toBoolean(-2));
        assertEquals(true, state.toBoolean(-1));
        state.pop(2);
    }

    /// The key is per state, so the same pointer stringifies differently in two of them.
    @Test
    void keyIsPerState() {
        try (var a = new AutoCloseableState(); var b = new AutoCloseableState()) {
            a.state.pushLightUserData(12345);
            b.state.pushLightUserData(12345);

            assertNotEquals(a.state.toStringRepr(-1), b.state.toStringRepr(-1));
        }
    }

    private static final class AutoCloseableState implements AutoCloseable {
        private final LuaState state = LuaState.newState();

        @Override
        public void close() {
            state.close();
        }
    }
}
