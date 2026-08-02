package net.hollowcube.luau;

import static net.hollowcube.luau.TestHelpers.eval;
import static net.hollowcube.luau.TestHelpers.load;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Stack shuffling, raw table access and value comparison.
///
/// Note the native library is built with assertions on, so handing these an index which is
/// out of range or a value of the wrong type is not an error the binding reports - it trips
/// an `api_check` and takes the process down. The tests here stay inside the contract.
@LuaStateParam
class TestLuaStackOps {

    /// The stack contents as a string, one character per slot, for the shuffling tests.
    private static String stack(LuaState state) {
        var sb = new StringBuilder();
        for (int i = 1; i <= state.top(); i++) sb.append(state.toString(i));
        return sb.toString();
    }

    private static void push(LuaState state, String... values) {
        for (String value : values) state.pushString(value);
    }

    @Nested
    class Indexing {

        @Test
        void absIndex(LuaState state) {
            push(state, "a", "b", "c");

            assertEquals(3, state.absIndex(-1));
            assertEquals(1, state.absIndex(-3));
            assertEquals(2, state.absIndex(2));
        }

        /// Pseudo-indices are not stack relative, so they pass through unchanged.
        @Test
        void absIndexPseudo(LuaState state) {
            assertEquals(LuaState.REGISTRY_INDEX, state.absIndex(LuaState.REGISTRY_INDEX));
        }

        @Test
        void top(LuaState state) {
            assertEquals(0, state.top());

            push(state, "a", "b", "c");
            assertEquals(3, state.top());

            state.top(1);
            assertEquals(1, state.top());
            assertEquals("a", state.toString(-1));
        }

        /// Growing the stack with [LuaState#top(int)] fills the new slots with nil.
        @Test
        void topGrow(LuaState state) {
            push(state, "a");
            state.top(3);

            assertEquals(3, state.top());
            assertTrue(state.isNil(2));
            assertTrue(state.isNil(3));
        }
    }

    @Nested
    class Shuffling {

        @Test
        void pushValue(LuaState state) {
            push(state, "a", "b");
            state.pushValue(1);

            assertEquals("aba", stack(state));
            assertTrue(state.rawEqual(1, 3));
        }

        /// Moves the top value down to the index, shifting everything above it up.
        @Test
        void insert(LuaState state) {
            push(state, "a", "b", "c", "X");
            state.insert(2);

            assertEquals("aXbc", stack(state));
        }

        @Test
        void remove(LuaState state) {
            push(state, "a", "b", "c");
            state.remove(2);

            assertEquals("ac", stack(state));
            assertEquals(2, state.top());
        }

        /// Pops the top value into the index, unlike [LuaState#insert(int)] overwriting
        /// rather than shifting.
        @Test
        void replace(LuaState state) {
            push(state, "a", "b", "c", "X");
            state.replace(2);

            assertEquals("aXc", stack(state));
            assertEquals(3, state.top());
        }

        @Test
        void pop(LuaState state) {
            push(state, "a", "b", "c");
            state.pop(2);

            assertEquals("a", stack(state));
        }
    }

    @Nested
    class Comparison {

        @Test
        void equalPrimitives(LuaState state) {
            push(state, "hello", "hello");
            state.pushNumber(1);
            state.pushNumber(2);

            assertTrue(state.equal(1, 2));
            assertFalse(state.equal(1, 3));
            assertFalse(state.equal(3, 4));
        }

        @Test
        void lessThan(LuaState state) {
            state.pushNumber(1);
            state.pushNumber(2);
            push(state, "a", "b");

            assertTrue(state.lessThan(1, 2));
            assertFalse(state.lessThan(2, 1));
            assertFalse(state.lessThan(1, 1));
            assertTrue(state.lessThan(3, 4), "strings compare lexicographically");
        }

        @Test
        void lessThanIncomparable(LuaState state) {
            state.pushNumber(1);
            state.pushString("a");

            var err = assertThrows(LuaError.class, () -> state.lessThan(1, 2));
            assertEquals("attempt to compare number < string", err.getMessage());
        }

        /// [LuaState#equal(int, int)] and [LuaState#lessThan(int, int)] respect metamethods,
        /// [LuaState#rawEqual(int, int)] does not.
        @Test
        void metamethods(LuaState state) {
            state.openLibs();
            eval(state, """
                    local mt = {
                        __eq = function() return true end,
                        __lt = function() return true end,
                    }
                    a = setmetatable({}, mt)
                    b = setmetatable({}, mt)
                    """);

            state.getGlobal("a");
            state.getGlobal("b");

            assertTrue(state.equal(1, 2));
            assertTrue(state.lessThan(1, 2));
            assertFalse(state.rawEqual(1, 2));
            assertTrue(state.rawEqual(1, 1));
        }
    }

    @Nested
    class RawAccess {

        @Test
        void createTable(LuaState state) {
            state.createTable(4, 2);

            assertEquals(1, state.top());
            assertTrue(state.isTable(-1));
            assertEquals(0, state.len(-1), "the hints do not add entries");
        }

        @Test
        void rawSetI(LuaState state) {
            state.newTable();
            state.pushString("one");
            state.rawSetI(1, 1);
            state.pushString("two");
            state.rawSetI(1, 2);

            assertEquals(1, state.top(), "the value is popped");
            assertEquals(2, state.len(1));

            assertEquals(LuaType.STRING, state.rawGetI(1, 1));
            assertEquals("one", state.toString(-1));
            state.pop(1);

            assertEquals(LuaType.NIL, state.rawGetI(1, 3));
        }

        @Test
        void rawSetField(LuaState state) {
            state.newTable();
            state.pushString("value");
            state.rawSetField(1, "key");

            assertEquals(1, state.top(), "the value is popped");
            assertEquals(LuaType.STRING, state.rawGetField(1, "key"));
            assertEquals("value", state.toString(-1));
        }

        @Test
        void rawSet(LuaState state) {
            state.newTable();
            state.pushString("key");
            state.pushString("value");
            state.rawSet(1);

            assertEquals(1, state.top(), "both the key and value are popped");

            state.pushString("key");
            assertEquals(LuaType.STRING, state.rawGet(1));
            assertEquals("value", state.toString(-1));
        }

        /// Raw access bypasses `__index`/`__newindex`, which [LuaState#getTable(int)] and
        /// [LuaState#setTable(int)] honour.
        @Test
        void rawBypassesMetamethods(LuaState state) {
            state.openLibs();
            eval(state, """
                    t = setmetatable({}, { __index = function() return "from index" end })
                    """);
            state.getGlobal("t");

            assertEquals(LuaType.STRING, state.getField(1, "missing"));
            assertEquals("from index", state.toString(-1));
            state.pop(1);

            assertEquals(LuaType.NIL, state.rawGetField(1, "missing"));
            state.pop(1);

            state.pushString("missing");
            assertEquals(LuaType.NIL, state.rawGet(1));
        }

        @Test
        void clearTable(LuaState state) {
            state.newTable();
            state.pushString("value");
            state.rawSetField(1, "key");
            state.pushString("one");
            state.rawSetI(1, 1);

            state.clearTable(1);

            assertEquals(0, state.len(1));
            assertEquals(LuaType.NIL, state.rawGetField(1, "key"));
        }

        @Test
        void cloneTable(LuaState state) {
            state.newTable();
            state.pushString("value");
            state.rawSetField(1, "key");

            state.cloneTable(1);
            assertEquals(2, state.top());
            assertFalse(state.rawEqual(1, 2), "a clone is a new table");

            assertEquals(LuaType.STRING, state.rawGetField(2, "key"));
            assertEquals("value", state.toString(-1));
            state.pop(1);

            // Shallow and independent: clearing the clone leaves the original alone.
            state.clearTable(2);
            assertEquals(LuaType.NIL, state.rawGetField(2, "key"));
            state.pop(1);
            assertEquals(LuaType.STRING, state.rawGetField(1, "key"));
        }
    }

    @Nested
    class Iteration {

        private static void fill(LuaState state) {
            state.newTable();
            state.pushString("one");
            state.rawSetI(1, 1);
            state.pushString("two");
            state.rawSetI(1, 2);
            state.pushString("value");
            state.rawSetField(1, "key");
        }

        /// [LuaState#next(int)] pops the previous key and pushes the next key/value pair,
        /// so the loop starts from a nil key and ends with the stack as it began.
        @Test
        void next(LuaState state) {
            fill(state);

            var seen = new LinkedHashMap<String, String>();
            state.pushNil();
            while (state.next(1)) {
                var key = state.isString(-2) ? state.toString(-2) :
                        Integer.toString(state.toInteger(-2));
                seen.put(key, state.toString(-1));
                state.pop(1); // leave the key for the next iteration
            }

            assertEquals(Map.of("1", "one", "2", "two", "key", "value"), new HashMap<>(seen));
            assertEquals(1, state.top());
        }

        /// [LuaState#rawIter(int, int)] is an index based iterator rather than a key based
        /// one: it pushes the pair at `iter` and returns the next index, or -1 at the end.
        @Test
        void rawIter(LuaState state) {
            fill(state);

            var seen = new LinkedHashMap<String, String>();
            int iter = 0;
            int last = 0;
            while ((iter = state.rawIter(1, iter)) >= 0) {
                var key = state.isString(-2) ? state.toString(-2) :
                        Integer.toString(state.toInteger(-2));
                seen.put(key, state.toString(-1));
                state.pop(2); // rawIter does not consume the previous pair
                last = iter;
            }

            assertEquals(Map.of("1", "one", "2", "two", "key", "value"), new HashMap<>(seen));
            assertEquals(3, last, "one index per entry");
            assertEquals(1, state.top());
        }

        @Test
        void rawIterEmpty(LuaState state) {
            state.newTable();
            assertEquals(-1, state.rawIter(1, 0));
        }

        @Test
        void rawIterPastEnd(LuaState state) {
            fill(state);
            assertEquals(-1, state.rawIter(1, 100));
        }
    }

    @Nested
    class Functions {

        @Test
        void cloneFunction(LuaState state) {
            load(state, "return 42");

            state.cloneFunction(1);
            assertEquals(2, state.top());
            assertTrue(state.isLuaFunction(2));
            assertFalse(state.rawEqual(1, 2), "a clone is a new closure");

            state.call(0, 1);
            assertEquals(42, state.toInteger(-1), "the clone runs the same chunk");
            state.pop(1);

            state.call(0, 1);
            assertEquals(42, state.toInteger(-1), "and the original still works");
        }
    }

    @Nested
    class Library {

        /// [LuaState#register(Map)] fills the table below the functions it pushes, so the
        /// target table has to be on the stack already.
        @Test
        void register(LuaState state, Arena arena) {
            state.newTable();
            state.register(Map.of("answer", LuaFunc.wrap(L -> {
                L.pushInteger(42);
                return 1;
            }, "answer", arena)));

            assertEquals(1, state.top());
            assertEquals(LuaType.FUNCTION, state.rawGetField(1, "answer"));

            state.call(0, 1);
            assertEquals(42, state.toInteger(-1));
        }

        /// [LuaState#register(String, Map)] is not implemented: it neither creates the named
        /// table nor registers anything.
        @Test
        void registerNamedIsUnimplemented(LuaState state, Arena arena) {
            state.openLibs();
            state.register("lib", Map.of("answer", LuaFunc.wrap(L -> 0, "answer", arena)));

            assertEquals(0, state.top());
            state.getGlobal("lib");
            assertTrue(state.isNil(-1));
        }

        /// Creates each missing element of the dotted path as a table and leaves the
        /// innermost one on the stack.
        @Test
        void findTable(LuaState state) {
            state.newTable();

            assertNull(state.findTable(1, "a.b.c", 1));
            assertEquals(2, state.top());
            assertTrue(state.isTable(-1));

            state.top(1);
            assertEquals(LuaType.TABLE, state.rawGetField(1, "a"));
            assertEquals(LuaType.TABLE, state.rawGetField(-1, "b"));
            assertEquals(LuaType.TABLE, state.rawGetField(-1, "c"));
        }

        /// A second lookup finds the tables the first one created rather than replacing them.
        @Test
        void findTableExisting(LuaState state) {
            state.newTable();

            state.findTable(1, "a.b", 1);
            state.pushString("value");
            state.rawSetField(2, "key");
            state.top(1);

            state.findTable(1, "a.b", 1);
            assertEquals(LuaType.STRING, state.rawGetField(2, "key"));
            assertEquals("value", state.toString(-1));
        }

        /// On a conflict the offending component is returned and nothing is pushed.
        @Test
        void findTableConflict(LuaState state) {
            state.newTable();
            state.pushString("not a table");
            state.rawSetField(1, "x");

            assertEquals("x.y", state.findTable(1, "x.y", 1));
            assertEquals(1, state.top());
        }
    }

    @Nested
    class TypeNames {

        @Test
        void primitives(LuaState state) {
            push(state, "hello");
            state.newTable();
            state.pushNumber(1);

            assertEquals("string", state.typeName(1));
            assertEquals("table", state.typeName(2));
            assertEquals("number", state.typeName(3));
        }

        @Test
        void none(LuaState state) {
            assertEquals("no value", state.typeName(1));
        }

        @Test
        void plainUserData(LuaState state) {
            state.newUserData(new Object());
            assertEquals("userdata", state.typeName(1));
        }

        /// Unlike [LuaType#typeName()] this reports the `__type` of the value's metatable
        /// where it has one.
        @Test
        void userDataWithMetaTable(LuaState state) {
            state.newTable();
            state.pushString("MyType");
            state.setField(-2, "__type");
            state.setUserDataMetaTable(42);

            state.newUserDataTaggedWithMetatable(new Object(), 42);

            assertEquals(LuaType.USERDATA, state.type(-1));
            assertEquals("userdata", state.type(-1).typeName());
            assertEquals("MyType", state.typeName(-1));
        }

        @Test
        void nil(LuaState state) {
            state.pushNil();
            assertEquals("nil", state.typeName(1));
        }
    }
}
