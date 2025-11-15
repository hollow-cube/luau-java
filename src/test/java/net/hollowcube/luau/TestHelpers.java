package net.hollowcube.luau;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.hollowcube.luau.compiler.LuauCompiler;
import org.intellij.lang.annotations.Language;
import org.opentest4j.AssertionFailedError;

class TestHelpers {

    public static void eval(LuaState state, @Language("luau") String code) {
        eval(state, code, 0);
    }

    public static void eval(
        LuaState state,
        @Language("luau") String code,
        int nret
    ) {
        load(state, code);
        state.call(0, nret);
    }

    public static void load(LuaState state, @Language("luau") String code) {
        var bytecode = assertDoesNotThrow(() ->
            LuauCompiler.DEFAULT.compile(code)
        );
        state.load("test.luau", bytecode);
    }

    public static String printStackTrace(Throwable t, int maxLines) {
        var sw = new StringWriter();

        // replace all non-lua line numbers with 0
        var st = t.getStackTrace();
        for (int i = 0; i < st.length; i++) {
            if ("lua".equals(st[i].getClassName())) continue;
            st[i] = new StackTraceElement(
                st[i].getClassName(),
                st[i].getMethodName(),
                st[i].getFileName(),
                0
            );
        }
        t.setStackTrace(st);

        t.printStackTrace(new PrintWriter(sw));
        var string = sw.toString();

        int endIndex = -1;
        for (int i = 0; i < maxLines; i++) {
            endIndex = string.indexOf('\n', endIndex + 1);
            if (endIndex == -1) break;
        }
        if (endIndex == -1) return string;
        return string.substring(0, endIndex);
    }

    public static void assertMatches(String expectedPattern, String actual) {
        expectedPattern = expectedPattern.trim();
        actual = actual.trim();
        var pattern = Pattern.compile(
            Arrays.stream(expectedPattern.split("/\\.\\+/"))
                .map(Pattern::quote)
                .collect(Collectors.joining(".+"))
        );
        if (!pattern.matcher(actual).matches()) throw new AssertionFailedError(
            null,
            expectedPattern,
            actual
        );
    }
}
