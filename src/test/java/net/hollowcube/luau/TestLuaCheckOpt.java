package net.hollowcube.luau;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The `check*`/`opt*` argument checking family.
///
/// These are normally called on the arguments of a [LuaFunc], but they only read the stack
/// by index, so pushing the "arguments" directly is equivalent and keeps the assertions in
/// the test rather than inside an upcall (where a failure would take the VM down with it).
///
/// A raised argument error carries the index it was given, and reads either
/// `missing argument #N (T expected)` when there is nothing at that index or
/// `invalid argument #N (T expected, got X)` when there is. Each wrong-type case gets its
/// own state because the raise leaves the error value behind on the stack.
@LuaStateParam
class TestLuaCheckOpt {

    record MyUserData(int value) {}

    @Nested
    class CheckAny {

        /// Any value at all satisfies it, including nil - only absence is an error.
        @Test
        void acceptsAnyPresentValue(LuaState state) {
            state.pushString("hello");
            state.checkAny(1);

            state.pushNil();
            state.checkAny(2);
        }

        @Test
        void missingArgument(LuaState state) {
            var err = assertThrows(LuaError.class, () -> state.checkAny(1));
            assertEquals("missing argument #1", err.getMessage());
        }
    }

    @Nested
    class CheckType {

        @Test
        void matchingType(LuaState state) {
            state.newTable();
            state.checkType(1, LuaType.TABLE);
        }

        /// Unlike the `check*` accessors, nil is a type like any other here.
        @Test
        void matchingNil(LuaState state) {
            state.pushNil();
            state.checkType(1, LuaType.NIL);
        }

        @Test
        void wrongType(LuaState state) {
            state.pushString("hello");
            var err = assertThrows(LuaError.class, () ->
                    state.checkType(1, LuaType.TABLE));
            assertEquals("invalid argument #1 (table expected, got string)", err.getMessage());
        }

        @Test
        void missingArgument(LuaState state) {
            var err = assertThrows(LuaError.class, () ->
                    state.checkType(1, LuaType.TABLE));
            assertEquals("missing argument #1 (table expected)", err.getMessage());
        }
    }

    @Nested
    class Booleans {

        @Test
        void checkBoolean(LuaState state) {
            state.pushBoolean(true);
            state.pushBoolean(false);
            assertTrue(state.checkBoolean(1));
            assertFalse(state.checkBoolean(2));
        }

        /// No truthiness coercion, unlike [LuaState#toBoolean(int)].
        @Test
        void checkBooleanWrongType(LuaState state) {
            state.pushString("hello");
            var err = assertThrows(LuaError.class, () -> state.checkBoolean(1));
            assertEquals("invalid argument #1 (boolean expected, got string)", err.getMessage());
        }

        @Test
        void checkBooleanNil(LuaState state) {
            state.pushNil();
            var err = assertThrows(LuaError.class, () -> state.checkBoolean(1));
            assertEquals("invalid argument #1 (boolean expected, got nil)", err.getMessage());
        }

        @Test
        void checkBooleanMissing(LuaState state) {
            var err = assertThrows(LuaError.class, () -> state.checkBoolean(2));
            assertEquals("missing argument #2 (boolean expected)", err.getMessage());
        }

        @Test
        void optBooleanPresent(LuaState state) {
            state.pushBoolean(false);
            assertFalse(state.optBoolean(1, true));
        }

        /// A missing argument and an explicit nil both take the default here, matching
        /// every other `opt*` method.
        @Test
        void optBooleanMissingOrNil(LuaState state) {
            assertTrue(state.optBoolean(1, true));

            state.pushNil();
            assertTrue(state.optBoolean(1, true));
            assertFalse(state.optBoolean(1, false));
        }

        /// [LuaState#optBoolean(int, boolean)] on a present non-boolean raises out of
        /// `luaL_optboolean` without the wrapper the rest of the family uses, so the
        /// longjmp finds no protected frame and aborts the process rather than throwing.
        @Test
        void optBooleanWrongType(LuaState state) {
            state.pushString("hello");
            var err = assertThrows(LuaError.class, () -> state.optBoolean(1, true));
            assertEquals("invalid argument #1 (boolean expected, got string)", err.getMessage());
        }
    }

    @Nested
    class Numbers {

        @Test
        void checkNumber(LuaState state) {
            state.pushNumber(3.5);
            assertEquals(3.5, state.checkNumber(1));
        }

        /// The number accessors accept a string which parses as a number, as the VM does.
        @Test
        void checkNumberFromString(LuaState state) {
            state.pushString("42");
            assertEquals(42.0, state.checkNumber(1));
            assertEquals(42, state.checkInteger(1));
            assertEquals(42L, state.checkUnsigned(1));
        }

        @Test
        void checkNumberWrongType(LuaState state) {
            state.pushBoolean(true);
            state.pushString("hello");
            var err = assertThrows(LuaError.class, () -> state.checkNumber(2));
            assertEquals("invalid argument #2 (number expected, got string)", err.getMessage());
        }

        @Test
        void checkNumberMissing(LuaState state) {
            var err = assertThrows(LuaError.class, () -> state.checkNumber(1));
            assertEquals("missing argument #1 (number expected)", err.getMessage());
        }

        @Test
        void checkInteger(LuaState state) {
            state.pushNumber(42.7);
            assertEquals(42, state.checkInteger(1), "truncates towards zero");
        }

        @Test
        void checkIntegerWrongType(LuaState state) {
            state.pushString("hello");
            var err = assertThrows(LuaError.class, () -> state.checkInteger(1));
            assertEquals("invalid argument #1 (number expected, got string)", err.getMessage());
        }

        @Test
        void checkIntegerMissing(LuaState state) {
            var err = assertThrows(LuaError.class, () -> state.checkInteger(3));
            assertEquals("missing argument #3 (number expected)", err.getMessage());
        }

        @Test
        void checkUnsigned(LuaState state) {
            state.pushNumber(-1);
            assertEquals(4294967295L, state.checkUnsigned(1), "wraps into the u32 range");
        }

        @Test
        void checkUnsignedWrongType(LuaState state) {
            state.newTable();
            var err = assertThrows(LuaError.class, () -> state.checkUnsigned(1));
            assertEquals("invalid argument #1 (number expected, got table)", err.getMessage());
        }

        @Test
        void checkUnsignedMissing(LuaState state) {
            var err = assertThrows(LuaError.class, () -> state.checkUnsigned(1));
            assertEquals("missing argument #1 (number expected)", err.getMessage());
        }

        @Test
        void optNumberPresent(LuaState state) {
            state.pushNumber(3.5);
            assertEquals(3.5, state.optNumber(1, 1.5));
        }

        @Test
        void optNumberMissingOrNil(LuaState state) {
            assertEquals(1.5, state.optNumber(1, 1.5));

            state.pushNil();
            assertEquals(1.5, state.optNumber(1, 1.5));
        }

        /// Unlike [LuaState#optBoolean(int, boolean)], the rest of the family falls back to
        /// the default for a *wrong* type too, not just for a missing one.
        @Test
        void optNumberWrongType(LuaState state) {
            state.pushString("hello");
            assertEquals(1.5, state.optNumber(1, 1.5));
        }

        @Test
        void optIntegerPresent(LuaState state) {
            state.pushNumber(42.7);
            assertEquals(42, state.optInteger(1, 7));
        }

        @Test
        void optIntegerMissingOrNil(LuaState state) {
            assertEquals(7, state.optInteger(1, 7));

            state.pushNil();
            assertEquals(7, state.optInteger(1, 7));

            state.pushString("hello");
            assertEquals(7, state.optInteger(2, 7));
        }

        @Test
        void optUnsignedPresent(LuaState state) {
            state.pushNumber(-1);
            assertEquals(4294967295L, state.optUnsigned(1, 7));
        }

        @Test
        void optUnsignedMissingOrNil(LuaState state) {
            assertEquals(7L, state.optUnsigned(1, 7));

            state.pushNil();
            assertEquals(7L, state.optUnsigned(1, 7));

            state.pushString("hello");
            assertEquals(7L, state.optUnsigned(2, 7));
        }

        /// `integer` is its own type, so a plain number is not one - see
        /// [LuaState#isInteger64(int)].
        @Test
        void optInteger64Present(LuaState state) {
            state.pushInteger64(9);
            assertEquals(9L, state.optInteger64(1, 7));
        }

        @Test
        void optInteger64MissingOrNil(LuaState state) {
            assertEquals(7L, state.optInteger64(1, 7));

            state.pushNil();
            assertEquals(7L, state.optInteger64(1, 7));

            state.pushNumber(9);
            assertEquals(7L, state.optInteger64(2, 7), "a number is not an integer");
        }

        @Test
        void checkInteger64WrongType(LuaState state) {
            state.pushNumber(9);
            var err = assertThrows(LuaError.class, () -> state.checkInteger64(1));
            assertEquals("invalid argument #1 (integer expected, got number)", err.getMessage());
        }
    }

    @Nested
    class Strings {

        @Test
        void checkString(LuaState state) {
            state.pushString("hello");
            assertEquals("hello", state.checkString(1));
        }

        /// No number to string coercion, matching [LuaState#toString(int)] rather than
        /// `luaL_checklstring`.
        @Test
        void checkStringNumber(LuaState state) {
            state.pushBoolean(true);
            state.pushNumber(42.7);
            var err = assertThrows(LuaError.class, () -> state.checkString(2));
            assertEquals("invalid argument #2 (string expected, got number)", err.getMessage());
        }

        @Test
        void checkStringMissing(LuaState state) {
            var err = assertThrows(LuaError.class, () -> state.checkString(1));
            assertEquals("missing argument #1 (string expected)", err.getMessage());
        }

        @Test
        void optStringPresent(LuaState state) {
            state.pushString("hello");
            assertEquals("hello", state.optString(1, "def"));
        }

        @Test
        void optStringMissingOrNil(LuaState state) {
            assertEquals("def", state.optString(1, "def"));

            state.pushNil();
            assertEquals("def", state.optString(1, "def"));

            state.pushNumber(5);
            assertEquals("def", state.optString(2, "def"), "numbers are not coerced");
        }
    }

    @Nested
    class Vectors {

        @Test
        void checkVector(LuaState state) {
            state.pushVector(1, 2, 3);
            assertArrayEquals(new float[]{1, 2, 3}, state.checkVector(1));
        }

        @Test
        void checkVectorWrongType(LuaState state) {
            state.pushString("hello");
            var err = assertThrows(LuaError.class, () -> state.checkVector(1));
            assertEquals("invalid argument #1 (vector expected, got string)", err.getMessage());
        }

        @Test
        void checkVectorMissing(LuaState state) {
            var err = assertThrows(LuaError.class, () -> state.checkVector(2));
            assertEquals("missing argument #2 (vector expected)", err.getMessage());
        }

        @Test
        void optVectorPresent(LuaState state) {
            state.pushVector(1, 2, 3);
            assertArrayEquals(new float[]{1, 2, 3}, state.optVector(1, new float[]{9, 9, 9}));
        }

        @Test
        void optVectorMissingOrNil(LuaState state) {
            assertArrayEquals(new float[]{9, 9, 9}, state.optVector(1, new float[]{9, 9, 9}));

            state.pushNil();
            assertArrayEquals(new float[]{9, 9, 9}, state.optVector(1, new float[]{9, 9, 9}));

            state.pushNumber(5);
            assertArrayEquals(new float[]{9, 9, 9}, state.optVector(2, new float[]{9, 9, 9}));
        }
    }

    @Nested
    class Buffers {

        @Test
        void checkBuffer(LuaState state) {
            state.newBuffer(8);
            var buffer = state.checkBuffer(1);
            assertEquals(8, buffer.capacity());
        }

        @Test
        void checkBufferWrongType(LuaState state) {
            state.pushString("hello");
            var err = assertThrows(LuaError.class, () -> state.checkBuffer(1));
            assertEquals("invalid argument #1 (buffer expected, got string)", err.getMessage());
        }

        @Test
        void checkBufferMissing(LuaState state) {
            var err = assertThrows(LuaError.class, () -> state.checkBuffer(3));
            assertEquals("missing argument #3 (buffer expected)", err.getMessage());
        }
    }

    @Nested
    class Options {

        private static final List<String> OPTIONS = List.of("alpha", "beta", "gamma");

        @Test
        void checkOption(LuaState state) {
            state.pushString("beta");
            assertEquals(1, state.checkOption(1, null, OPTIONS));
        }

        /// With a default the argument becomes optional, so a missing or nil argument
        /// resolves the default against the list instead of raising.
        @Test
        void checkOptionDefault(LuaState state) {
            assertEquals(0, state.checkOption(1, "alpha", OPTIONS));

            state.pushNil();
            assertEquals(2, state.checkOption(1, "gamma", OPTIONS));
        }

        @Test
        void checkOptionUnknown(LuaState state) {
            state.pushString("delta");
            var err = assertThrows(LuaError.class, () ->
                    state.checkOption(1, null, OPTIONS));
            assertEquals("invalid argument #1 (invalid option 'delta')", err.getMessage());
        }

        @Test
        void checkOptionMissingWithoutDefault(LuaState state) {
            var err = assertThrows(LuaError.class, () ->
                    state.checkOption(2, null, OPTIONS));
            assertEquals("missing argument #2 (string expected)", err.getMessage());
        }
    }

    @Nested
    class UserData {

        @Test
        void checkUserData(LuaState state) {
            var value = new MyUserData(12345);

            assertTrue(state.newMetaTable("MyType"));
            state.pop(1);

            state.newUserData(value);
            state.getMetaTable("MyType");
            state.setMetaTable(-2);

            assertSame(value, state.checkUserData(1, "MyType"));
        }

        @Test
        void checkUserDataWrongMetaTable(LuaState state) {
            assertTrue(state.newMetaTable("MyType"));
            state.pop(1);

            state.newUserData(new MyUserData(12345));
            state.getMetaTable("MyType");
            state.setMetaTable(-2);

            var err = assertThrows(LuaError.class, () -> state.checkUserData(1, "Other"));
            assertEquals("invalid argument #1 (Other expected, got userdata)", err.getMessage());
        }

        @Test
        void checkUserDataWrongType(LuaState state) {
            state.pushString("hello");
            var err = assertThrows(LuaError.class, () -> state.checkUserData(1, "MyType"));
            assertEquals("invalid argument #1 (MyType expected, got string)", err.getMessage());
        }

        @Test
        void checkUserDataMissing(LuaState state) {
            var err = assertThrows(LuaError.class, () -> state.checkUserData(1, "MyType"));
            assertEquals("missing argument #1 (MyType expected)", err.getMessage());
        }

        @Test
        void checkUserDataTagged(LuaState state) {
            var value = new MyUserData(12345);

            state.newUserDataTagged(value, 7);
            assertSame(value, state.checkUserDataTagged(1, 7));
        }

        /// The type in the message comes from [LuaState#getUserDataName(int)], which is the
        /// `__type` of the tag's metatable where there is one.
        @Test
        void checkUserDataTaggedWrongType(LuaState state) {
            state.newTable();
            state.pushString("Tagged");
            state.setField(-2, "__type");
            state.setUserDataMetaTable(7);

            state.pushString("hello");
            var err = assertThrows(LuaError.class, () -> state.checkUserDataTagged(1, 7));
            assertEquals("invalid argument #1 (Tagged expected, got string)", err.getMessage());
        }

        /// A tag with no metatable reports the bare `userdata`, which reads oddly against a
        /// value which really is userdata - just with another tag.
        @Test
        void checkUserDataTaggedWrongTag(LuaState state) {
            assertEquals("userdata", state.getUserDataName(8));

            state.newUserDataTagged(new MyUserData(12345), 7);
            var err = assertThrows(LuaError.class, () -> state.checkUserDataTagged(1, 8));
            assertEquals("invalid argument #1 (userdata expected, got userdata)", err.getMessage());
        }

        @Test
        void checkUserDataTaggedMissing(LuaState state) {
            var err = assertThrows(LuaError.class, () -> state.checkUserDataTagged(2, 7));
            assertEquals("missing argument #2 (userdata expected)", err.getMessage());
        }
    }
}
