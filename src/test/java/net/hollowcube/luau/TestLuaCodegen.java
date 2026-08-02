package net.hollowcube.luau;

import static net.hollowcube.luau.TestHelpers.load;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.lang.foreign.Arena;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// Native codegen lives in the same library as the VM (see native/CMakeLists.txt), and is
/// only reachable if Luau.CodeGen was actually built and its symbols exported.
@LuaStateParam
class TestLuaCodegen {

    /// A chunk the compiler does not mark cold, so codegen actually lowers it.
    private static final String HOT = """
            local total = 0
            for i = 1, 10 do total += i * 2 end
            return total
            """;

    @BeforeEach
    void assumeCodegen() {
        assumeTrue(LuaState.codegenSupported(), "native codegen is unsupported here");
    }

    @Test
    void compilesAndRuns(LuaState state) {
        state.codegenCreate();
        load(state, """
                local function fib(n)
                    if n < 2 then return n end
                    return fib(n - 1) + fib(n - 2)
                end
                return fib(20)
                """);

        assertEquals(LuaCodegenResult.SUCCESS, state.codegenCompile(-1));
        state.call(0, 1);

        assertEquals(6765, state.toInteger(-1));
        state.pop(1);
    }

    /// Nothing is compiled twice, so a second pass over the same function has no work left.
    @Test
    void secondCompileIsANoop(LuaState state) {
        state.codegenCreate();
        load(state, HOT);

        assertEquals(LuaCodegenResult.SUCCESS, state.codegenCompile(-1));
        assertEquals(LuaCodegenResult.NOTHING_TO_COMPILE, state.codegenCompile(-1));
        state.pop(1);
    }

    /// The compiler marks functions it does not consider worth lowering as cold, and codegen
    /// skips them by default, so a trivial chunk has nothing to compile.
    @Test
    void coldFunctionsAreSkipped(LuaState state) {
        state.codegenCreate();
        load(state, "return 1");

        assertEquals(LuaCodegenResult.NOTHING_TO_COMPILE, state.codegenCompile(-1));
        state.pop(1);
    }

    @Test
    void compileWithoutCreateReportsNotInitialized(LuaState state) {
        load(state, HOT);

        assertEquals(LuaCodegenResult.NOT_INITIALIZED, state.codegenCompile(-1));
        state.pop(1);
    }

    @Test
    void compileRejectsNonFunction(LuaState state) {
        state.codegenCreate();
        state.pushNumber(1);

        assertThrows(IllegalArgumentException.class, () -> state.codegenCompile(-1));
        state.pop(1);
    }

    /// Java closures are pushed as a native trampoline (luaW_dispatch) rather than as a
    /// plain lua_CFunction, so calling one from natively compiled code goes through a call
    /// path the interpreter does not use.
    @Test
    void callsJavaFunctionFromNativeCode(LuaState state, Arena arena) {
        state.codegenCreate();
        state.pushFunction(LuaFunc.wrap(s -> {
            s.pushNumber(s.checkNumber(1) * 2);
            return 1;
        }, "double", arena));
        state.setGlobal("double");

        load(state, """
                local total = 0
                for i = 1, 10 do total += double(i) end
                return total
                """);
        assertEquals(LuaCodegenResult.SUCCESS, state.codegenCompile(-1));
        state.call(0, 1);

        assertEquals(110, state.toInteger(-1));
        state.pop(1);
    }

    /// The trampoline raises the error after the Java frame has returned. Native code
    /// reaches the C call through its own lowering, so this exercises a second path.
    @Test
    void propagatesJavaErrorThroughNativeCode(LuaState state, Arena arena) {
        state.codegenCreate();
        state.pushFunction(LuaFunc.wrap(s -> {
            throw s.error("boom");
        }, "boom", arena));
        state.setGlobal("boom");

        load(state, """
                local total = 0
                for i = 1, 10 do total += i * 2 end
                boom()
                return total
                """);
        assertEquals(LuaCodegenResult.SUCCESS, state.codegenCompile(-1));

        final LuaError error = assertThrows(LuaError.class, () -> state.call(0, 0));
        assertEquals("boom", error.getMessage());
    }
}
