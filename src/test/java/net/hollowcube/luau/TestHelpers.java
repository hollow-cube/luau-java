package net.hollowcube.luau;

import net.hollowcube.luau.compiler.LuauCompiler;
import org.intellij.lang.annotations.Language;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestHelpers {

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

    public static void assertMatches(String expected, String actual) {
        // W*ndows
        expected = expected.trim().replaceAll("\r\n", "\n");
        actual = actual.trim().replaceAll("\r\n", "\n");
        assertEquals(expected, actual);
    }
}
