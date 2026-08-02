package net.hollowcube.luau;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.hollowcube.luau.compiler.CoverageLevel;
import net.hollowcube.luau.compiler.LuauCompiler;
import org.junit.jupiter.api.Test;

/// Coverage counters live in the bytecode, so they only exist if the compiler was asked to
/// emit them, and they accumulate for as long as the chunk is loaded.
@LuaStateParam
class TestLuaCoverage {

    private static final LuauCompiler INSTRUMENTED = LuauCompiler.builder()
            .coverageLevel(CoverageLevel.STATEMENT)
            .build();

    private static final String SOURCE = """
            local function taken(n)
                return n + 1
            end
            local function skipped(n)
                return n - 1
            end
            local total = 0
            for i = 1, 3 do
                total = taken(total)
            end
            return total
            """;

    private static void load(LuaState state, LuauCompiler compiler, String source) {
        state.load("test.luau", assertDoesNotThrow(() -> compiler.compile(source)));
    }

    @Test
    void countsStatements(LuaState state) {
        load(state, INSTRUMENTED, SOURCE);
        state.pushValue(-1);
        state.call(0, 1);
        assertEquals(3, state.toInteger(-1));
        state.pop(1);

        final List<LuaCoverage> coverage = state.getCoverage(-1);
        state.pop(1);

        // The chunk itself, then taken and skipped nested inside it.
        assertEquals(3, coverage.size());
        assertEquals(0, coverage.getFirst().depth());
        assertTrue(coverage.stream().skip(1).allMatch(c -> c.depth() == 1));

        final LuaCoverage taken = byName(coverage, "taken");
        final LuaCoverage skipped = byName(coverage, "skipped");

        // `return n + 1` is line 2, `return n - 1` is line 5
        assertEquals(3, taken.hits(2));
        assertEquals(0, skipped.hits(5));
    }

    /// A line with no statement on it is reported as never instrumented, which is distinct
    /// from a statement that never ran.
    @Test
    void distinguishesUninstrumentedFromUnhit(LuaState state) {
        load(state, INSTRUMENTED, """
                local x = 1
                -- a comment
                if x == 2 then
                    x = 3
                end
                return x
                """);
        state.pushValue(-1);
        state.call(0, 1);
        state.pop(1);

        final LuaCoverage chunk = state.getCoverage(-1).getFirst();
        state.pop(1);

        assertEquals(1, chunk.hits(1));
        assertEquals(LuaCoverage.NOT_INSTRUMENTED, chunk.hits(2));
        assertEquals(0, chunk.hits(4));
    }

    /// Without the compiler option there are no counting instructions at all, so every line
    /// reports as never instrumented even though the chunk ran.
    @Test
    void reportsNothingWithoutTheCompilerOption(LuaState state) {
        load(state, LuauCompiler.DEFAULT, SOURCE);
        state.pushValue(-1);
        state.call(0, 1);
        state.pop(1);

        final List<LuaCoverage> coverage = state.getCoverage(-1);
        state.pop(1);

        assertTrue(coverage.stream()
                .flatMapToInt(c -> java.util.stream.IntStream.of(c.hits()))
                .allMatch(h -> h == LuaCoverage.NOT_INSTRUMENTED));
    }

    /// Counters are cumulative, never reset by reading them.
    @Test
    void countsAccumulateAcrossCalls(LuaState state) {
        load(state, INSTRUMENTED, "return 1");

        state.pushValue(-1);
        state.call(0, 1);
        state.pop(1);
        assertEquals(1, state.getCoverage(-1).getFirst().hits(1));

        state.pushValue(-1);
        state.call(0, 1);
        state.pop(1);
        assertEquals(2, state.getCoverage(-1).getFirst().hits(1));

        state.pop(1);
    }

    @Test
    void rejectsNonLuaFunction(LuaState state) {
        state.pushNumber(1);

        assertThrows(IllegalArgumentException.class, () -> state.getCoverage(-1));
        state.pop(1);
    }

    private static LuaCoverage byName(List<LuaCoverage> coverage, String name) {
        return coverage.stream()
                .filter(c -> name.equals(c.function()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no coverage for " + name));
    }
}
