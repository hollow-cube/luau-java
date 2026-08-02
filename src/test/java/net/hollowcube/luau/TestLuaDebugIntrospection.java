package net.hollowcube.luau;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.hollowcube.luau.compiler.DebugLevel;
import net.hollowcube.luau.compiler.LuauCompiler;
import org.junit.jupiter.api.Test;

/// Reading locals, arguments and upvalues off the live stack. Everything here is addressed
/// by frame level, so it only means anything from inside a Java function called by Lua.
///
/// Note the probes only record - an assertion which fails inside a [LuaFunc] takes the VM
/// down with it, so the checks happen after the call returns.
@LuaStateParam
class TestLuaDebugIntrospection {

    /// Local and upvalue *names* are only in the bytecode at [DebugLevel#DEBUGGER].
    private static final LuauCompiler DEBUGGABLE = LuauCompiler.builder()
            .debugLevel(DebugLevel.DEBUGGER)
            .build();

    private static void run(LuaState state, LuauCompiler compiler, String source) {
        state.load("test.luau", assertDoesNotThrow(() -> compiler.compile(source)));
        state.call(0, 0);
    }

    private static void install(LuaState state, Arena arena, String name, Consumer<LuaState> probe) {
        state.pushFunction(LuaFunc.wrap(s -> {
            probe.accept(s);
            return 0;
        }, name, arena));
        state.setGlobal(name);
    }

    @Test
    void stackDepthGrowsWithNesting(LuaState state, Arena arena) {
        final List<Integer> depths = new ArrayList<>();
        install(state, arena, "probe", s -> depths.add(s.stackDepth()));

        run(state, DEBUGGABLE, "probe()");
        run(state, DEBUGGABLE, """
                local function outer()
                    local function inner() probe() end
                    inner()
                end
                outer()
                """);

        assertEquals(2, depths.size());
        assertTrue(depths.get(1) > depths.getFirst(), "nested call should be deeper");
    }

    /// Level 1 is the caller of the Java function, which is where the interesting locals are.
    @Test
    void readsCallerLocals(LuaState state, Arena arena) {
        final List<String> seen = new ArrayList<>();
        install(state, arena, "probe", s -> {
            for (int n = 1; ; n++) {
                final String name = s.getLocal(1, n);
                if (name == null) break;
                seen.add(name + "=" + s.toStringRepr(-1));
                s.pop(1);
            }
        });

        run(state, DEBUGGABLE, """
                local function target()
                    local alpha = 1
                    local beta = "two"
                    probe()
                end
                target()
                """);

        assertEquals(List.of("alpha=1", "beta=two"), seen);
    }

    /// Without debugger-level debug info the names are simply not in the bytecode, and
    /// there is nothing for getLocal to find.
    @Test
    void localsAreInvisibleWithoutDebugInfo(LuaState state, Arena arena) {
        final List<String> seen = new ArrayList<>();
        install(state, arena, "probe", s -> seen.add(String.valueOf(s.getLocal(1, 1))));

        run(state, LuauCompiler.DEFAULT, """
                local function target()
                    local alpha = 1
                    probe()
                end
                target()
                """);

        assertEquals(List.of("null"), seen);
    }

    @Test
    void writesCallerLocals(LuaState state, Arena arena) {
        final List<String> assigned = new ArrayList<>();
        install(state, arena, "bump", s -> {
            s.pushNumber(99);
            assigned.add(String.valueOf(s.setLocal(1, 1)));
        });

        state.load("test.luau", assertDoesNotThrow(() -> DEBUGGABLE.compile("""
                local function target()
                    local alpha = 1
                    bump()
                    return alpha
                end
                return target()
                """)));
        state.call(0, 1);

        assertEquals(List.of("alpha"), assigned);
        assertEquals(99, state.toInteger(-1));
        state.pop(1);
    }

    /// Arguments are read off the frame's registers rather than by name, so this one works
    /// at any debug level - and covers varargs past the declared parameters.
    @Test
    void readsCallerArguments(LuaState state, Arena arena) {
        final List<String> seen = new ArrayList<>();
        install(state, arena, "probe", s -> {
            for (int n = 1; s.getArgument(1, n); n++) {
                seen.add(s.toStringRepr(-1));
                s.pop(1);
            }
        });

        run(state, LuauCompiler.DEFAULT, """
                local function target(a, b, ...)
                    probe()
                end
                target("x", "y", "z")
                """);

        assertEquals(List.of("x", "y", "z"), seen);
    }

    @Test
    void outOfRangeReportsNothing(LuaState state, Arena arena) {
        final List<String> results = new ArrayList<>();
        install(state, arena, "probe", s -> {
            results.add(String.valueOf(s.getLocal(1, 99)));
            results.add(String.valueOf(s.getLocal(999, 1)));
            results.add(String.valueOf(s.getArgument(1, 99)));
        });

        run(state, DEBUGGABLE, """
                local function target() probe() end
                target()
                """);

        assertEquals(List.of("null", "null", "false"), results);
    }

    @Test
    void readsAndWritesUpvalues(LuaState state) {
        state.load("test.luau", assertDoesNotThrow(() -> DEBUGGABLE.compile("""
                local function make(captured)
                    return function() return captured end
                end
                return make("before")
                """)));
        state.call(0, 1);

        assertEquals("captured", state.getUpvalue(-1, 1));
        assertEquals("before", state.toString(-1));
        state.pop(1);

        assertNull(state.getUpvalue(-1, 2));

        // -2, not -1: the closure is under the value about to be popped.
        state.pushString("after");
        assertEquals("captured", state.setUpvalue(-2, 1));

        state.pushValue(-1);
        state.call(0, 1);
        assertEquals("after", state.toString(-1));
        state.pop(2);
    }

    /// An upvalue with no recorded name still exists and is still readable, it just reports
    /// an empty name - unlike a local, which vanishes entirely.
    @Test
    void upvaluesExistWithoutDebugInfo(LuaState state) {
        state.load("test.luau", assertDoesNotThrow(() -> LuauCompiler.DEFAULT.compile("""
                local function make(captured)
                    return function() return captured end
                end
                return make("before")
                """)));
        state.call(0, 1);

        assertEquals("", state.getUpvalue(-1, 1));
        assertEquals("before", state.toString(-1));
        assertFalse(state.isNil(-1));
        state.pop(2);
    }
}
