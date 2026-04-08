package net.hollowcube.luau.preempt;

import net.hollowcube.luau.*;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;

import static net.hollowcube.luau.TestHelpers.eval;
import static org.junit.jupiter.api.Assertions.*;

@LuaStateParam
public class TestLuaPreempt {

    final Arena arena = Arena.ofAuto();

    void setupCallbacks(LuaState state, LuaCallbacks.Preempt.Handler handler){
        state.callbacks().preempt(LuaCallbacks.Preempt.allocate(handler, arena));
    }

    @Test
    void testYieldingPreempt(LuaState state) {
        setupCallbacks(state, (LuaState s, int gc) -> true);

        var thrown = assertThrows(LuaError.class, () ->
                eval(state, """
                    while true do end
                """));

        assertEquals("attempt to yield across metamethod/C-call boundary", thrown.getMessage());
    }

    @Test
    void testNonYieldingPreempt(LuaState state) {
        setupCallbacks(state, (LuaState s, int gc) -> false);

        assertDoesNotThrow(() ->
                eval(state, """
                    local i = 0
                    while i < 100 do
                        i = i + 1
                    end
                    return i
                """, 1));

        assertTrue(state.checkStack(1));
        assertEquals(100, state.toInteger(-1));
    }

    @Test
    void testErroringPreempt(LuaState state) {
        setupCallbacks(state, (LuaState s, int gc) -> {
            throw new LuaError("Preempting with an error");
        });
        var thrown = assertThrows(LuaError.class, () ->
                eval(state, """
                    while true do end
                """));

        assertEquals("Preempting with an error", thrown.getMessage());
    }
}
