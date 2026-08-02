package net.hollowcube.luau;

import static net.hollowcube.luau.TestHelpers.eval;
import static net.hollowcube.luau.internal.vm.luaujava_h.luaW_pushcclosurek;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import org.junit.jupiter.api.Test;

/// Java closures are pushed as a native dispatch trampoline which reserves the first two
/// upvalue slots for the Java function and continuation, so [LuaState#upvalueIndex(int)]
/// is offset from the raw `lua_upvalueindex`. Getting that offset wrong reads the
/// trampoline's own light userdata instead of the caller's upvalue.
@LuaStateParam
class TestLuaFuncUpvalues {

    @Test
    void upvaluesSkipDispatchSlots(LuaState state, Arena arena) {
        final LuaFunc func = LuaFunc.wrap(
            s -> {
                s.pushValue(LuaState.upvalueIndex(1));
                s.pushValue(LuaState.upvalueIndex(2));
                return 2;
            },
            "upvals",
            arena
        );

        state.pushString("first");
        state.pushString("second");
        luaW_pushcclosurek(
            state.L(),
            func.funcRef(),
            func.debugNameRef(),
            2,
            MemorySegment.NULL
        );
        state.setGlobal("upvals");

        eval(state, "a, b = upvals()");

        state.getGlobal("a");
        assertEquals("first", state.toString(-1));
        state.getGlobal("b");
        assertEquals("second", state.toString(-1));
        state.pop(2);
    }
}
