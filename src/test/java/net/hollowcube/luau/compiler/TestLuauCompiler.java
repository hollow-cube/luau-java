package net.hollowcube.luau.compiler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TestLuauCompiler {

    @Test
    void testCompileEmpty() throws LuauCompileException {
        LuauCompiler.DEFAULT.compile("");
    }

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
}
