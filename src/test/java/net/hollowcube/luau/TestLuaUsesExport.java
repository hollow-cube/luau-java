package net.hollowcube.luau;

import static net.hollowcube.luau.TestHelpers.load;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/// The compiler flags a top level chunk which declares `export` values, which lets a
/// require implementation tell an exporting module from a returning one before running it.
@LuaStateParam
class TestLuaUsesExport {

    @Test
    void trueForExportingChunk(LuaState state) {
        load(state, """
                export local answer = 42
                """);

        assertTrue(state.usesExport(-1));
        state.pop(1);
    }

    @Test
    void falseForOrdinaryChunk(LuaState state) {
        load(state, "return 42");

        assertFalse(state.usesExport(-1));
        state.pop(1);
    }

    @Test
    void falseForNonFunction(LuaState state) {
        state.pushNumber(1);

        assertFalse(state.usesExport(-1));
        state.pop(1);
    }
}
