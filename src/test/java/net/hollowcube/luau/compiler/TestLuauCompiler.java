package net.hollowcube.luau.compiler;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.hollowcube.luau.LuaCoverage;
import net.hollowcube.luau.LuaError;
import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaStateParam;
import net.hollowcube.luau.LuaType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@LuaStateParam
class TestLuauCompiler {

    private static byte[] compile(LuauCompiler compiler, String source) {
        return assertDoesNotThrow(() -> compiler.compile(source));
    }

    /// Loads and runs the chunk, leaving `nret` results on the stack.
    private static void run(
        LuaState state,
        LuauCompiler compiler,
        String source,
        int nret
    ) {
        state.load("test.luau", compile(compiler, source));
        state.call(0, nret);
    }

    @Test
    void testCompileEmpty() throws LuauCompileException {
        LuauCompiler.DEFAULT.compile("");
    }

    @Test
    void compilesFromBytesAndStringIdentically() throws LuauCompileException {
        assertArrayEquals(
            LuauCompiler.DEFAULT.compile("return 1"),
            LuauCompiler.DEFAULT.compile("return 1".getBytes())
        );
    }

    //region compile errors

    @Test
    void testCompileFail() {
        var exc = assertThrows(LuauCompileException.class, () ->
            LuauCompiler.DEFAULT.compile("+")
        );
        assertEquals(
            ":1: Expected identifier when parsing expression, got '+'",
            exc.getMessage()
        );
    }

    @Test
    void unterminatedStringIsReported() {
        var exc = assertThrows(LuauCompileException.class, () ->
            LuauCompiler.DEFAULT.compile("local s = 'oops")
        );
        assertEquals(":1: Malformed string; did you forget to finish it?", exc.getMessage());
    }

    @Test
    void unclosedBlockReportsTheLineOfTheOpener() {
        var exc = assertThrows(LuauCompileException.class, () ->
            LuauCompiler.DEFAULT.compile("""
                local function f()
                    return 1
                """)
        );
        assertEquals(
            ":3: Expected 'end' (to close 'function' at line 1), got <eof>",
            exc.getMessage()
        );
    }

    @Test
    void breakOutsideALoopIsReported() {
        var exc = assertThrows(LuauCompileException.class, () ->
            LuauCompiler.DEFAULT.compile("break")
        );
        assertEquals(":1: break statement must be inside a loop", exc.getMessage());
    }

    @Test
    void assignmentToACallIsReported() {
        var exc = assertThrows(LuauCompileException.class, () ->
            LuauCompiler.DEFAULT.compile("f() = 1")
        );
        assertEquals(
            ":1: Expected identifier when parsing expression, got '='",
            exc.getMessage()
        );
    }

    /// Compile errors are reported for the first failure only, and the line number is the
    /// one in the source rather than in the chunk given to [LuaState#load].
    @Test
    void errorLineIsReportedForLaterLines() {
        var exc = assertThrows(LuauCompileException.class, () ->
            LuauCompiler.DEFAULT.compile("""
                local a = 1
                local b = 2
                local c = ?
                """)
        );
        assertEquals(":3: Expected identifier when parsing expression, got '?'", exc.getMessage());
    }

    //endregion

    //region optimization level

    @ParameterizedTest
    @EnumSource(OptimizationLevel.class)
    void everyOptimizationLevelCompilesAndRuns(OptimizationLevel level, LuaState state) {
        final LuauCompiler compiler = LuauCompiler.builder()
            .optimizationLevel(level)
            .build();

        run(state, compiler, """
            local function add(a, b) return a + b end
            return add(1, 2) + #"abc"
            """, 1);

        assertEquals(6, state.toInteger(-1));
    }

    /// Constant folding and inlining only happen above [OptimizationLevel#NONE], which is
    /// visible as a different (and for this source, shorter) instruction stream.
    @Test
    void optimizationLevelChangesTheEmittedBytecode() {
        final String source = """
            local function double(x) return x * 2 end
            return double(21)
            """;

        final byte[] none = compile(
            LuauCompiler.builder().optimizationLevel(OptimizationLevel.NONE).build(),
            source);
        final byte[] baseline = compile(
            LuauCompiler.builder().optimizationLevel(OptimizationLevel.BASELINE).build(),
            source);
        final byte[] maximum = compile(
            LuauCompiler.builder().optimizationLevel(OptimizationLevel.MAXIMUM).build(),
            source);

        assertFalse(Arrays.equals(none, baseline), "NONE and BASELINE agree");
        assertFalse(Arrays.equals(baseline, maximum), "BASELINE and MAXIMUM agree");
    }

    //endregion

    //region debug level

    @ParameterizedTest
    @EnumSource(DebugLevel.class)
    void everyDebugLevelCompilesAndRuns(DebugLevel level, LuaState state) {
        final LuauCompiler compiler = LuauCompiler.builder()
            .debugLevel(level)
            .build();

        run(state, compiler, "return 1 + 1", 1);

        assertEquals(2, state.toInteger(-1));
    }

    /// Each step up adds more debug info to the chunk.
    @Test
    void debugLevelGrowsTheBytecode() {
        final String source = """
            local function outer(argument)
                local captured = argument
                return function() return captured end
            end
            return outer(1)()
            """;

        final int none = compile(
            LuauCompiler.builder().debugLevel(DebugLevel.NONE).build(), source).length;
        final int backtrace = compile(
            LuauCompiler.builder().debugLevel(DebugLevel.BACKTRACE).build(), source).length;
        final int debugger = compile(
            LuauCompiler.builder().debugLevel(DebugLevel.DEBUGGER).build(), source).length;

        assertTrue(none < backtrace, "backtrace info should be larger than none");
        assertTrue(backtrace < debugger, "debugger info should be larger than backtrace");
    }

    /// Local variable *names* only survive at [DebugLevel#DEBUGGER].
    @Test
    void debugLevelDebuggerKeepsLocalNames(LuaState state, Arena arena) {
        final List<String> names = new ArrayList<>();
        state.pushFunction(LuaFunc.wrap(s -> {
            // Level 1 is the Lua function which called the probe.
            final String name = s.getLocal(1, 1);
            names.add(String.valueOf(name));
            if (name != null) s.pop(1); // nothing is pushed when there is no such local
            return 0;
        }, "probe", arena));
        state.setGlobal("probe");

        final String source = """
            local function f(argument)
                probe()
            end
            f(1)
            """;

        run(state, LuauCompiler.builder().debugLevel(DebugLevel.DEBUGGER).build(), source, 0);
        run(state, LuauCompiler.builder().debugLevel(DebugLevel.BACKTRACE).build(), source, 0);

        assertEquals(List.of("argument", "null"), names);
    }

    //endregion

    //region coverage level

    @ParameterizedTest
    @EnumSource(CoverageLevel.class)
    void everyCoverageLevelCompilesAndRuns(CoverageLevel level, LuaState state) {
        final LuauCompiler compiler = LuauCompiler.builder()
            .coverageLevel(level)
            .build();

        run(state, compiler, "local x = 1\nreturn x + 1", 1);

        assertEquals(2, state.toInteger(-1));
    }

    /// Coverage counters are only emitted above [CoverageLevel#NONE], and the expression
    /// level instruments strictly more than the statement level.
    @Test
    void coverageLevelControlsInstrumentation(LuaState state) {
        final String source = """
            local x = 1
            local y = x == 1 and 2 or 3
            return y
            """;

        assertEquals(LuaCoverage.NOT_INSTRUMENTED, hits(state, CoverageLevel.NONE, source, 2));
        assertEquals(1, hits(state, CoverageLevel.STATEMENT, source, 2));
        assertEquals(1, hits(state, CoverageLevel.STATEMENT_AND_EXPRESSION, source, 2));

        final int statement = compile(
            LuauCompiler.builder().coverageLevel(CoverageLevel.STATEMENT).build(),
            source).length;
        final int expression = compile(
            LuauCompiler.builder()
                .coverageLevel(CoverageLevel.STATEMENT_AND_EXPRESSION)
                .build(),
            source).length;
        assertTrue(
            statement < expression,
            "expression coverage should instrument more than statement coverage");
    }

    private static int hits(LuaState state, CoverageLevel level, String source, int line) {
        state.load(
            "test.luau",
            compile(LuauCompiler.builder().coverageLevel(level).build(), source));
        state.pushValue(-1);
        state.call(0, 1);
        state.pop(1);

        final List<LuaCoverage> coverage = state.getCoverage(-1);
        state.pop(1); // the chunk
        return coverage.getFirst().hits(line);
    }

    //endregion

    //region type info level

    @ParameterizedTest
    @EnumSource(TypeInfoLevel.class)
    void everyTypeInfoLevelCompilesAndRuns(TypeInfoLevel level, LuaState state) {
        final LuauCompiler compiler = LuauCompiler.builder()
            .typeInfoLevel(level)
            .build();

        run(state, compiler, """
            local function typed(x: number): number
                return x + 1
            end
            return typed(1)
            """, 1);

        assertEquals(2, state.toInteger(-1));
    }

    /// A plain (non native) module only carries type information at
    /// [TypeInfoLevel#ALL_MODULES].
    @Test
    void allModulesTypeInfoGrowsTheBytecode() {
        final String source = """
            local function typed(x: number, s: string): number
                return x + #s
            end
            return typed(1, "a")
            """;

        final int nativeOnly = compile(
            LuauCompiler.builder().typeInfoLevel(TypeInfoLevel.NATIVE_MODULES).build(),
            source).length;
        final int allModules = compile(
            LuauCompiler.builder().typeInfoLevel(TypeInfoLevel.ALL_MODULES).build(),
            source).length;

        assertTrue(nativeOnly < allModules, "type info should add to the bytecode");
    }

    //endregion

    //region vectors

    /// With a vector constructor configured the compiler folds a constant call into a
    /// vector constant, so the chunk does not need the constructor to exist at runtime.
    @Test
    void vectorCtorFoldsConstantVectors(LuaState state) {
        final LuauCompiler compiler = LuauCompiler.builder()
            .optimizationLevel(OptimizationLevel.MAXIMUM)
            .vectorCtor("vector")
            .build();

        run(state, compiler, "return vector(1, 2, 3)", 1);

        assertEquals(LuaType.VECTOR, state.type(-1));
        assertArrayEquals(new float[] { 1, 2, 3 }, state.toVector(-1));
    }

    @Test
    void vectorLibNamespacesTheCtor(LuaState state) {
        final LuauCompiler compiler = LuauCompiler.builder()
            .optimizationLevel(OptimizationLevel.MAXIMUM)
            .vectorLib("Vector3")
            .vectorCtor("new")
            .build();

        run(state, compiler, "return Vector3.new(4, 5, 6)", 1);

        assertEquals(LuaType.VECTOR, state.type(-1));
        assertArrayEquals(new float[] { 4, 5, 6 }, state.toVector(-1));
    }

    /// Without the option the same source is a plain global call, which fails at runtime.
    @Test
    void withoutVectorCtorTheCallIsLeftAlone(LuaState state) {
        final LuaError error = assertThrows(LuaError.class, () ->
            run(state, LuauCompiler.DEFAULT, "return vector(1, 2, 3)", 1));

        assertEquals("attempt to call a nil value", error.getMessage());
    }

    /// The vector type name only feeds the type tables, so it is visible in the bytecode
    /// rather than at runtime.
    @Test
    void vectorTypeAffectsTypeInfo() {
        final String source = """
            local function f(v: Vector3): Vector3
                return v
            end
            return f
            """;

        final byte[] without = compile(
            LuauCompiler.builder().typeInfoLevel(TypeInfoLevel.ALL_MODULES).build(),
            source);
        final byte[] with = compile(
            LuauCompiler.builder()
                .typeInfoLevel(TypeInfoLevel.ALL_MODULES)
                .vectorType("Vector3")
                .build(),
            source);

        assertFalse(Arrays.equals(without, with), "vectorType did not change the type info");
    }

    //endregion

    //region mutable globals

    /// Marking a global mutable disables the import optimization for fields read through
    /// it, so the field is fetched from the global table on every access.
    @Test
    void mutableGlobalsDisableTheImportOptimization(LuaState state) {
        final String source = "return math.floor(1.5)";

        final byte[] imported = compile(LuauCompiler.DEFAULT, source);
        final byte[] mutable = compile(
            LuauCompiler.builder().mutableGlobals("math").build(),
            source);

        assertFalse(Arrays.equals(imported, mutable), "mutableGlobals had no effect");

        state.openLibs();
        run(state, LuauCompiler.builder().mutableGlobals("math").build(), source, 1);
        assertEquals(1, state.toInteger(-1));
    }

    @Test
    void mutableGlobalsReplacesThePreviousValue() {
        final String source = "return math.floor(1.5) + os.time()";

        final byte[] both = compile(
            LuauCompiler.builder().mutableGlobals("math", "os").build(),
            source);
        final byte[] replaced = compile(
            LuauCompiler.builder()
                .mutableGlobals("math", "os")
                .mutableGlobals(List.of("math"))
                .build(),
            source);

        assertFalse(Arrays.equals(both, replaced), "mutableGlobals was not replaced");
    }

    @Test
    void userdataTypesAreAcceptedForTypeInfo() {
        final String source = """
            local function f(v: MyUserdata): number
                return 1
            end
            return f
            """;

        final byte[] without = compile(
            LuauCompiler.builder().typeInfoLevel(TypeInfoLevel.ALL_MODULES).build(),
            source);
        final byte[] with = compile(
            LuauCompiler.builder()
                .typeInfoLevel(TypeInfoLevel.ALL_MODULES)
                .userdataTypes("MyUserdata")
                .build(),
            source);

        assertNotEquals(
            Arrays.toString(without),
            Arrays.toString(with),
            "userdataTypes did not reach the type info");
    }

    //endregion
}
