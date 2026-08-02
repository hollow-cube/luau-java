package net.hollowcube.luau;

import org.jetbrains.annotations.Nullable;

/// Coverage data for a single function, as reported by [LuaState#getCoverage(int)].
///
/// @param function    the debug name of the function, or null for anonymous functions and
///                    the top level chunk
/// @param lineDefined the source line the function was defined on
/// @param depth       nesting depth below the function coverage was requested for, which is
///                    0 for that function itself
/// @param hits        execution counts indexed by source line; see [#hits(int)]
public record LuaCoverage(
    @Nullable String function,
    int lineDefined,
    int depth,
    int[] hits
) {
    /// Never instrumented, the value [#hits(int)] returns for a line which carries no
    /// coverage instruction. Blank lines, comments and `end` all report this, as does
    /// every line if the chunk was compiled with [net.hollowcube.luau.compiler.CoverageLevel#NONE].
    public static final int NOT_INSTRUMENTED = -1;

    /// Returns how many times the given source line ran, or [#NOT_INSTRUMENTED].
    ///
    /// Zero is a meaningful result distinct from [#NOT_INSTRUMENTED]: the line was
    /// instrumented and never reached.
    public int hits(int line) {
        return line >= 0 && line < hits.length ? hits[line] : NOT_INSTRUMENTED;
    }

    /// One past the highest line number [#hits(int)] can report on.
    public int lineCount() {
        return hits.length;
    }
}
