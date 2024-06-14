package net.hollowcube.luau.compiler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LuauCompilerTest {

    private static final LuauCompiler compiler = LuauCompiler.DEFAULT;

    @Test
    void testCompileEmpty() {
        assertDoesNotThrow(() -> compiler.compile(""));
    }

    @Test
    void testCompileShortExample() {
        assertDoesNotThrow(() -> compiler.compile("""
                print('hello from lua')
                                
                local function add(a, b)
                    return a + b
                end
                print(add(1, 2))
                                
                local function sub(a: int, b: int): int
                    return a - b
                end
                """));
    }

    @Test
    void testInvalidSyntax() {
        var err = assertThrows(LuauCompileException.class, () -> compiler.compile("a"));
        assertEquals(":1: Incomplete statement: expected assignment or a function call", err.getMessage());
    }

}
