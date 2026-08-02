package net.hollowcube.luau;

import static net.hollowcube.luau.TestHelpers.load;
import static net.hollowcube.luau.internal.vm.luaujava_h.luaW_isinlined;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import org.junit.jupiter.api.Test;

/// The runtime inliner is driven by the VM's call-site feedback rather than by the caller,
/// so these run a call site past `LuauInlineHitsThreshold` (32) and check the result.
///
/// The call has to be inlinable for feedback to be emitted at all: the compiler only emits
/// `LOP_CALLFB` for a non-multret call inside a nested function, and only marks a callee
/// `LPF_INLINABLE` if it has no upvalues. `return callee(i)` is a multret call and would
/// silently never inline.
@LuaStateParam
class TestLuaJitInliner {

    private static final String HOT = """
            local function callee(x)
                return x * 2
            end
            local function caller(i)
                local v = callee(i)
                return v
            end
            local total = 0
            for i = 1, 100 do
                total += caller(i)
            end
            return caller, total
            """;

    @Test
    void inlinesHotCallSite(LuaState state) {
        state.jitInlinerCreate();
        load(state, HOT);
        state.call(0, 2);

        assertEquals(10100, state.toInteger(-1));
        assertTrue(luaW_isinlined(state.L(), -2) != 0, "caller was not inlined");
        state.pop(2);
    }

    @Test
    void doesNothingUntilEnabled(LuaState state) {
        load(state, HOT);
        state.call(0, 2);

        assertEquals(10100, state.toInteger(-1));
        assertFalse(luaW_isinlined(state.L(), -2) != 0, "inlined without jitInlinerCreate");
        state.pop(2);
    }

    @Test
    void disableStopsFurtherInlining(LuaState state) {
        state.jitInlinerCreate();
        state.jitInlinerDisable();
        load(state, HOT);
        state.call(0, 2);

        assertEquals(10100, state.toInteger(-1));
        assertFalse(luaW_isinlined(state.L(), -2) != 0, "inlined after jitInlinerDisable");
        state.pop(2);
    }

    /// Native codegen lowers `LOP_CALLFB` to a plain call, so a natively compiled function
    /// never records call feedback and the inliner never sees it. The two are effectively
    /// mutually exclusive - see [LuaState#jitInlinerCreate()].
    @Test
    void codegenSuppressesInlining(LuaState state) {
        if (!LuaState.codegenSupported()) return;

        state.jitInlinerCreate();
        state.codegenCreate();
        load(state, HOT);
        assertEquals(LuaCodegenResult.SUCCESS, state.codegenCompile(-1));
        state.call(0, 2);

        assertEquals(10100, state.toInteger(-1));
        assertFalse(luaW_isinlined(state.L(), -2) != 0, "inlined despite native codegen");
        state.pop(2);
    }

    /// Inlining rewrites the caller's bytecode while it is live, so a Java function called
    /// from an inlined caller still has to reach the dispatch trampoline correctly.
    @Test
    void callsJavaFunctionFromInlinedCode(LuaState state, Arena arena) {
        state.jitInlinerCreate();
        state.pushFunction(LuaFunc.wrap(s -> {
            s.pushNumber(s.checkNumber(1) + 1);
            return 1;
        }, "incr", arena));
        state.setGlobal("incr");

        load(state, """
                local function callee(x)
                    return incr(x)
                end
                local function caller(i)
                    local v = callee(i)
                    return v
                end
                local total = 0
                for i = 1, 100 do
                    total += caller(i)
                end
                return total
                """);
        state.call(0, 1);

        assertEquals(5150, state.toInteger(-1));
        state.pop(1);
    }
}
