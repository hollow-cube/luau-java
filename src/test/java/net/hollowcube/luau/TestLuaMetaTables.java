package net.hollowcube.luau;

import static net.hollowcube.luau.TestHelpers.eval;
import static org.junit.jupiter.api.Assertions.*;

import java.lang.foreign.Arena;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Metatables attached from Java, and the metamethods they install being driven both by Lua
/// code and by the corresponding host side entry points.
///
/// The recurring point here is which C API calls respect metamethods: `lua_gettable` and
/// friends do, the `raw*` family never does, and [LuaState#len(int)] does not either because
/// `lua_objlen` is raw even though `#t` from Lua is not.
@LuaStateParam
class TestLuaMetaTables {

    /// Pushes a table carrying `v = value`, with the metatable at `metaIndex` attached.
    private static void pushWithMeta(LuaState state, int metaIndex, int value) {
        state.newTable();
        state.pushInteger(value);
        state.setField(-2, "v");
        state.pushValue(metaIndex);
        state.setMetaTable(-2);
    }

    @Nested
    class Attaching {

        @Test
        void noMetaTableByDefault(LuaState state) {
            state.newTable();

            assertFalse(state.getMetaTable(-1));
            assertEquals(1, state.top(), "getMetaTable pushes nothing when there is none");
        }

        @Test
        void setThenGet(LuaState state) {
            state.newTable();

            state.newTable();
            state.pushString("Point");
            state.setField(-2, "__type");
            state.setMetaTable(-2);
            assertEquals(1, state.top(), "setMetaTable pops the metatable");

            assertTrue(state.getMetaTable(-1));
            assertEquals(LuaType.TABLE, state.type(-1));
            assertEquals(LuaType.STRING, state.getField(-1, "__type"));
            assertEquals("Point", state.toString(-1));
        }

        /// __type only renames userdata (and types with a global metatable), so a plain table
        /// keeps reporting as a table however its metatable is labelled.
        @Test
        void typeNameFollowsTypeOnUserDataOnly(LuaState state) {
            state.newTable();
            state.newTable();
            state.pushString("Point");
            state.setField(-2, "__type");
            state.pushValue(-1); // keep the metatable for the userdata below
            state.setMetaTable(-3);
            assertEquals("table", state.typeName(-2));

            state.newUserData(new Object());
            state.insert(-2); // [userdata] [metatable]
            state.setMetaTable(-2);
            assertEquals("Point", state.typeName(-1));
        }

        /// The metatable is a normal table, so nothing stops it being shared.
        @Test
        void sharedBetweenValues(LuaState state) {
            state.newTable(); // index 1, the metatable
            pushWithMeta(state, 1, 1);
            pushWithMeta(state, 1, 2);

            assertTrue(state.getMetaTable(2));
            assertTrue(state.getMetaTable(3));
            assertTrue(state.rawEqual(-1, -2));
            assertTrue(state.rawEqual(-1, 1));
        }

        @Test
        void newMetaTableRegistersUnderTypeName(LuaState state) {
            assertEquals(LuaType.NIL, state.getMetaTable("Point"));
            state.pop(1);

            assertTrue(state.newMetaTable("Point"), "first registration creates the table");
            state.pushString("Point");
            state.setField(-2, "__type");

            assertEquals(LuaType.TABLE, state.getMetaTable("Point"));
            assertTrue(state.rawEqual(-1, -2), "getMetaTable returns the registered table");
            assertEquals(LuaType.STRING, state.getField(-1, "__type"));
            assertEquals("Point", state.toString(-1));
            state.pop(2);

            assertFalse(state.newMetaTable("Point"), "second registration finds the existing one");
            assertTrue(state.rawEqual(-1, -2), "and leaves the existing table on the stack");
        }
    }

    @Nested
    class UserDataMetaTables {

        private record Entity(String name) {}

        @Test
        void tagMetaTableStartsUnset(LuaState state) {
            state.getUserDataMetaTable(7);

            assertTrue(state.isNil(-1));
        }

        @Test
        void tagMetaTableRoundTrips(LuaState state) {
            state.newTable();
            state.pushString("Entity");
            state.setField(-2, "__type");
            state.setUserDataMetaTable(7);
            assertEquals(0, state.top(), "setUserDataMetaTable pops the metatable");

            state.getUserDataMetaTable(7);
            assertEquals(LuaType.STRING, state.getField(-1, "__type"));
            assertEquals("Entity", state.toString(-1));
        }

        /// The tag metatable is only applied by the withMetatable constructor; a plain tagged
        /// userdata of the same tag has none.
        @Test
        void tagMetaTableIsNotAppliedImplicitly(LuaState state) {
            state.newTable();
            state.pushString("Entity");
            state.setField(-2, "__type");
            state.setUserDataMetaTable(7);

            state.newUserDataTagged(new Entity("bob"), 7);
            assertFalse(state.getMetaTable(-1));
            assertEquals("userdata", state.typeName(-1));
            state.pop(1);

            state.newUserDataTaggedWithMetatable(new Entity("bob"), 7);
            assertTrue(state.getMetaTable(-1));
            assertEquals("Entity", state.typeName(-2));
        }

        /// Untagged userdata gets its metatable the ordinary way instead.
        @Test
        void plainUserDataUsesSetMetaTable(LuaState state, Arena arena) {
            final Entity entity = new Entity("bob");
            state.newUserData(entity);

            state.newTable();
            state.pushString("Entity");
            state.setField(-2, "__type");
            state.pushFunction(LuaFunc.wrap(s -> {
                s.pushString(((Entity) s.toUserData(1)).name() + ":" + s.checkString(2));
                return 1;
            }, "__index", arena));
            state.setField(-2, "__index");
            state.setMetaTable(-2);

            assertEquals("Entity", state.typeName(-1));
            assertSame(entity, state.toUserData(-1));
            assertEquals(LuaType.STRING, state.getField(-1, "greet"));
            assertEquals("bob:greet", state.toString(-1));
        }
    }

    @Nested
    class Index {

        @Test
        void indexTableFiresThroughGetFieldButNotRawGetField(LuaState state) {
            state.newTable();

            state.newTable();
            state.newTable();
            state.pushString("fallback");
            state.setField(-2, "key");
            state.setField(-2, "__index");
            state.setMetaTable(-2);

            assertEquals(LuaType.STRING, state.getField(-1, "key"));
            assertEquals("fallback", state.toString(-1));
            state.pop(1);

            assertEquals(LuaType.NIL, state.rawGetField(-1, "key"), "rawGetField ignores __index");
            state.pop(1);

            state.pushString("key");
            assertEquals(LuaType.NIL, state.rawGet(-2), "rawGet ignores __index");
        }

        @Test
        void indexAsLuaFunction(LuaState state) {
            state.newTable();

            state.newTable();
            eval(state, "return function(_, key) return 'lua:' .. key end", 1);
            state.setField(-2, "__index");
            state.setMetaTable(-2);

            state.pushString("missing");
            assertEquals(LuaType.STRING, state.getTable(-2));
            assertEquals("lua:missing", state.toString(-1));
        }

        @Test
        void indexAsJavaFunction(LuaState state, Arena arena) {
            state.newTable();

            state.newTable();
            state.pushFunction(LuaFunc.wrap(s -> {
                s.pushString("java:" + s.checkString(2));
                return 1;
            }, "__index", arena));
            state.setField(-2, "__index");
            state.setMetaTable(-2);

            assertEquals(LuaType.STRING, state.getField(-1, "missing"));
            assertEquals("java:missing", state.toString(-1));
        }

        /// __index is only consulted for keys the table itself does not have.
        @Test
        void indexSkippedForPresentKeys(LuaState state, Arena arena) {
            state.newTable();
            state.pushString("real");
            state.setField(-2, "key");

            state.newTable();
            state.pushFunction(LuaFunc.wrap(s -> {
                throw s.error("__index should not have been called");
            }, "__index", arena));
            state.setField(-2, "__index");
            state.setMetaTable(-2);

            assertEquals(LuaType.STRING, state.getField(-1, "key"));
            assertEquals("real", state.toString(-1));
        }
    }

    @Nested
    class NewIndex {

        @Test
        void newIndexAsJavaFunction(LuaState state, Arena arena) {
            final Map<String, String> writes = new LinkedHashMap<>();

            state.newTable();
            state.newTable();
            state.pushFunction(LuaFunc.wrap(s -> {
                writes.put(s.checkString(2), s.checkString(3));
                return 0;
            }, "__newindex", arena));
            state.setField(-2, "__newindex");
            state.setMetaTable(-2);

            state.pushString("value");
            state.setField(-2, "key");
            assertEquals(Map.of("key", "value"), writes);
            assertEquals(LuaType.NIL, state.rawGetField(-1, "key"), "the write never landed");
            state.pop(1);

            state.pushString("other");
            state.pushString("value2");
            state.setTable(-3);
            assertEquals(Map.of("key", "value", "other", "value2"), writes);
        }

        @Test
        void rawSetBypassesNewIndex(LuaState state, Arena arena) {
            final Map<String, String> writes = new LinkedHashMap<>();

            state.newTable();
            state.newTable();
            state.pushFunction(LuaFunc.wrap(s -> {
                writes.put(s.checkString(2), s.checkString(3));
                return 0;
            }, "__newindex", arena));
            state.setField(-2, "__newindex");
            state.setMetaTable(-2);

            state.pushString("value");
            state.rawSetField(-2, "key");
            assertEquals(Map.of(), writes, "rawSetField ignores __newindex");
            assertEquals(LuaType.STRING, state.rawGetField(-1, "key"));
            state.pop(1);

            state.pushString("other");
            state.pushString("value2");
            state.rawSet(-3);
            assertEquals(Map.of(), writes, "rawSet ignores __newindex");
            assertEquals(LuaType.STRING, state.rawGetField(-1, "other"));
        }

        @Test
        void newIndexAsTableRedirectsTheWrite(LuaState state) {
            state.newTable(); // index 1, the write target
            state.newTable(); // index 2, the proxy

            state.newTable();
            state.pushValue(1);
            state.setField(-2, "__newindex");
            state.setMetaTable(2);

            state.pushString("value");
            state.setField(2, "key");

            assertEquals(LuaType.NIL, state.rawGetField(2, "key"));
            state.pop(1);
            assertEquals(LuaType.STRING, state.rawGetField(1, "key"));
            assertEquals("value", state.toString(-1));
        }
    }

    @Nested
    class Comparison {

        /// Luau reads __eq off both operands, so both need the metatable for it to fire.
        @Test
        void eqAsJavaFunction(LuaState state, Arena arena) {
            state.newTable(); // index 1, the shared metatable
            state.pushFunction(LuaFunc.wrap(s -> {
                s.getField(1, "v");
                s.getField(2, "v");
                s.pushBoolean(s.toInteger(-2) == s.toInteger(-1));
                return 1;
            }, "__eq", arena));
            state.setField(-2, "__eq");

            pushWithMeta(state, 1, 5); // index 2
            pushWithMeta(state, 1, 5); // index 3
            pushWithMeta(state, 1, 6); // index 4

            assertFalse(state.rawEqual(2, 3), "distinct tables are never raw equal");
            assertTrue(state.equal(2, 3));
            assertFalse(state.equal(2, 4));
        }

        /// A table without the metatable falls back to identity, since there is no shared
        /// metamethod to consult.
        @Test
        void eqNotFiredWithoutASharedMetaTable(LuaState state, Arena arena) {
            state.newTable(); // index 1, the metatable
            state.pushFunction(LuaFunc.wrap(s -> {
                s.pushBoolean(true);
                return 1;
            }, "__eq", arena));
            state.setField(-2, "__eq");

            pushWithMeta(state, 1, 5); // index 2
            state.newTable(); // index 3, no metatable

            assertFalse(state.equal(2, 3));
        }

        @Test
        void ltAsLuaFunction(LuaState state) {
            state.newTable(); // index 1, the shared metatable
            eval(state, "return function(a, b) return a.v < b.v end", 1);
            state.setField(-2, "__lt");

            pushWithMeta(state, 1, 5); // index 2
            pushWithMeta(state, 1, 6); // index 3

            assertTrue(state.lessThan(2, 3));
            assertFalse(state.lessThan(3, 2));
            assertFalse(state.lessThan(2, 2));
        }

        @Test
        void ltErrorsWithoutTheMetaMethod(LuaState state) {
            state.newTable();
            state.newTable();

            final LuaError error = assertThrows(LuaError.class, () -> state.lessThan(-2, -1));
            assertEquals("attempt to compare table < table", error.getMessage());
        }
    }

    @Nested
    class OtherMetaMethods {

        /// `#t` runs the metamethod but lua_objlen does not, so the two disagree on purpose.
        @Test
        void lenIsHonouredByLuaButNotByObjLen(LuaState state) {
            state.newTable();

            state.newTable();
            eval(state, "return function() return 42 end", 1);
            state.setField(-2, "__len");
            state.setMetaTable(-2);

            state.pushValue(-1);
            state.setGlobal("obj");
            eval(state, "return #obj", 1);
            assertEquals(42, state.toInteger(-1));
            state.pop(1);

            assertEquals(0, state.len(-1), "lua_objlen is raw");
        }

        @Test
        void callAsJavaFunction(LuaState state, Arena arena) {
            state.newTable();

            state.newTable();
            state.pushFunction(LuaFunc.wrap(s -> {
                // arg 1 is the callable itself, the declared arguments follow
                assertTrue(s.isTable(1));
                s.pushString("called:" + s.checkString(2));
                return 1;
            }, "__call", arena));
            state.setField(-2, "__call");
            state.setMetaTable(-2);

            state.pushValue(-1);
            state.pushString("x");
            state.call(1, 1);
            assertEquals("called:x", state.toString(-1));
        }

        @Test
        void toStringAsJavaFunction(LuaState state, Arena arena) {
            state.newTable();
            state.pushInteger(3);
            state.setField(-2, "v");

            state.newTable();
            state.pushFunction(LuaFunc.wrap(s -> {
                s.getField(1, "v");
                s.pushString("Point(" + s.toInteger(-1) + ")");
                return 1;
            }, "__tostring", arena));
            state.setField(-2, "__tostring");
            state.setMetaTable(-2);

            final int top = state.top();
            assertEquals("Point(3)", state.toStringRepr(-1));
            assertEquals(top, state.top(), "toStringRepr pops the string it was handed");

            assertNull(state.toString(-1), "the value itself is still a table");
        }

        @Test
        void concatAsLuaFunction(LuaState state) {
            state.newTable();
            state.pushString("obj");
            state.setField(-2, "name");

            state.newTable();
            eval(state, "return function(a, b) return a.name .. b end", 1);
            state.setField(-2, "__concat");
            state.setMetaTable(-2);

            state.pushString("!");
            state.concat(2);
            assertEquals("obj!", state.toString(-1));
        }

        @Test
        void concatErrorsWithoutTheMetaMethod(LuaState state) {
            state.newTable();
            state.pushString("!");

            final LuaError error = assertThrows(LuaError.class, () -> state.concat(2));
            assertEquals("attempt to concatenate table with string", error.getMessage());
        }
    }
}
