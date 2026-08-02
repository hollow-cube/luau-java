package net.hollowcube.luau;

import static net.hollowcube.luau.TestHelpers.eval;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.foreign.Arena;
import org.junit.jupiter.api.Test;

/// Checking userdata by tag rather than by metatable identity, and the name the VM reports
/// for a tag when that check fails.
@LuaStateParam
class TestLuaUserDataTags {

    private record Entity(String name) {}

    private static void registerTag(LuaState state, int tag, String typeName) {
        state.newTable();
        state.pushString(typeName);
        state.setField(-2, "__type");
        state.setUserDataMetaTable(tag);
    }

    @Test
    void nameDefaultsToUserdata(LuaState state) {
        assertEquals("userdata", state.getUserDataName(3));
    }

    @Test
    void nameComesFromMetatableType(LuaState state) {
        registerTag(state, 3, "Entity");

        assertEquals("Entity", state.getUserDataName(3));
    }

    @Test
    void checkAcceptsMatchingTag(LuaState state, Arena arena) {
        final Entity entity = new Entity("bob");
        registerTag(state, 3, "Entity");

        state.pushFunction(LuaFunc.wrap(s -> {
            assertSame(entity, s.checkUserDataTagged(1, 3));
            return 0;
        }, "accept", arena));
        state.setGlobal("accept");

        state.newUserDataTagged(entity, 3);
        state.setGlobal("entity");

        eval(state, "accept(entity)");
    }

    /// The failure message uses the tag's __type, which is the whole reason to prefer this
    /// over a bare tag comparison.
    @Test
    void checkReportsTheTagName(LuaState state, Arena arena) {
        registerTag(state, 3, "Entity");

        state.pushFunction(LuaFunc.wrap(s -> {
            s.checkUserDataTagged(1, 3);
            return 0;
        }, "accept", arena));
        state.setGlobal("accept");

        state.newUserDataTagged(new Entity("bob"), 4);
        state.setGlobal("wrong");

        final LuaError error = assertThrows(LuaError.class, () -> eval(state, "accept(wrong)"));
        assertEquals("invalid argument #1 to 'accept' (Entity expected, got userdata)", error.getMessage());
    }
}
