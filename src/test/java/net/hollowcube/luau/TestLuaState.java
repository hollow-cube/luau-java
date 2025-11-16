package net.hollowcube.luau;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;
import java.util.concurrent.atomic.AtomicInteger;

import static net.hollowcube.luau.TestHelpers.eval;
import static org.junit.jupiter.api.Assertions.*;

@LuaStateParam
class TestLuaState {

    @Test
    void pushReadNil(LuaState state) {
        state.pushNil();
        assertTrue(state.isNil(-1));
        assertFalse(state.isNone(-1));
        assertTrue(state.isNoneOrNil(-1));
    }

    @Test
    void checkStackOk(LuaState state) {
        assertTrue(state.checkStack(1));
    }

    @Test
    void checkStackFail(LuaState state) {
        assertFalse(state.checkStack(Integer.MAX_VALUE));
    }

    @Nested
    class BooleanValues {

        @Test
        void pushRead(LuaState state) {
            state.pushBoolean(true);
            assertTrue(state.isBoolean(-1));
            assertTrue(state.toBoolean(-1));
        }
    }

    @Nested
    class NumberValues {

        @Test
        void pushReadInteger(LuaState state) {
            state.pushInteger(42);
            assertTrue(state.isNumber(-1));
            assertEquals(42, state.toInteger(-1));
        }

        @Test
        void toIntegerNone(LuaState state) {
            state.pushBoolean(true);
            assertNull(state.toIntegerOrNull(-1));
        }

        @Test
        void pushReadUnsigned(LuaState state) {
            state.pushUnsigned(((long) Integer.MAX_VALUE) + 12);
            assertTrue(state.isNumber(-1));
            assertEquals(((long) Integer.MAX_VALUE) + 12, state.toUnsigned(-1));
        }

        @Test
        void toUnsignedNone(LuaState state) {
            state.pushBoolean(true);
            assertNull(state.toUnsignedOrNull(-1));
        }

        @Test
        void pushReadNumber(LuaState state) {
            state.pushNumber(3.1415);
            assertTrue(state.isNumber(-1));
            assertEquals(3.1415, state.toNumber(-1));
        }

        @Test
        void toNumberNone(LuaState state) {
            state.pushBoolean(true);
            assertNull(state.toNumberOrNull(-1));
        }

        @Test
        void integerToNumber(LuaState state) {
            state.pushInteger(42);
            assertEquals(42.0, state.toNumber(-1));
        }
    }

    @Nested
    class VectorValues {

        @Test
        void pushRead(LuaState state) {
            state.pushVector(new float[]{1, 2, 3});
            assertTrue(state.isVector(-1));
            assertArrayEquals(new float[]{1, 2, 3}, state.toVector(-1));
        }

        @Test
        void pushRead2(LuaState state) {
            state.pushVector(1, 2, 3);
            assertTrue(state.isVector(-1));
            assertArrayEquals(new float[]{1, 2, 3}, state.toVector(-1));
        }

        @Test
        void readNonVector(LuaState state) {
            state.pushBoolean(true);
            assertNull(state.toVector(-1));
        }
    }

    @Nested
    class StringValues {

        @Test
        void pushRead(LuaState state) {
            state.pushString("hello, world");
            assertTrue(state.isString(-1));
            assertEquals("hello, world", state.toString(-1));
            assertEquals("hello, world", state.unsafeToString(-1));
            assertEquals("hello, world", state.toStringRepr(-1));
        }

        @Test
        void intToStringWithUnsafe(LuaState state) {
            state.pushInteger(42);
            assertTrue(state.isNumber(-1));
            assertEquals("42", state.unsafeToString(-1));
            assertTrue(state.isString(-1));
        }

        @Test
        void readNonString(LuaState state) {
            state.pushBoolean(true);
            assertNull(state.toString(-1));
        }

        @Test
        void stringConcat(LuaState state) {
            state.pushString("hello");
            state.pushString(", ");
            state.pushString("world");
            state.concat(3);
            assertEquals("hello, world", state.toString(-1));
        }

        @Test
        void stringLen(LuaState state) {
            state.pushString("hello");
            assertEquals(5, state.len(-1));
        }

        @Test
        void nilToStringRepr(LuaState state) {
            state.pushNil();
            assertEquals("nil", state.toStringRepr(-1));
        }
    }

    @Nested
    class BufferValues {

        @Test
        void pushRead(LuaState state) {
            state.newBuffer(1024);
            assertTrue(state.isBuffer(-1));
            var buffer = state.toBuffer(-1);
            assertNotNull(buffer);
            assertEquals(1024, buffer.capacity());
        }

        @Test
        void readNonBuffer(LuaState state) {
            state.pushBoolean(true);
            assertNull(state.toBuffer(-1));
        }

        @Test
        void readBuffer(LuaState state) {
            state.openLibs();

            state.newBuffer(1024);
            state.setGlobal("theBuffer");

            eval(
                state,
                """
                    buffer.writei32(theBuffer, 32, buffer.len(theBuffer))
                    """
            );

            state.getGlobal("theBuffer");

            var buffer = state.toBuffer(-1);
            buffer = buffer;
            assertEquals(1024, buffer.getInt(32));
        }
    }

    @Nested
    class TableValues {

        @Test
        void pushRead(LuaState state) {
            state.newTable();
            assertTrue(state.isTable(-1));
            assertEquals(0, state.len(-1));
        }

        @Test
        void readNonTable(LuaState state) {
            state.pushNumber(1.234);
            assertEquals(0, state.len(-1));
            state.pushString("test"); // index key
            assertThrows(LuaError.class, () -> state.getTable(-2));
            assertThrows(LuaError.class, () -> state.getField(-2, "test"));
        }

        @Test
        void rawGetField(LuaState state) {
            state.newTable();
            state.pushString("world");
            state.setField(-2, "hello");

            assertEquals(LuaType.STRING, state.getField(-1, "hello"));
            state.pop(1);

            assertEquals(LuaType.STRING, state.rawGetField(-1, "hello"));
            state.pop(1);

            state.pushString("hello");
            assertEquals(LuaType.STRING, state.getTable(-2));
            state.pop(1);
            state.pushString("hello");
            assertEquals(LuaType.STRING, state.rawGet(-2));
            state.pop(1);
        }

        @Test
        void setOnReadOnly(LuaState state) {
            state.newTable();
            state.pushString("world");
            state.setField(-2, "hello");

            assertFalse(state.getReadOnly(-1));
            state.setReadOnly(-1, true);
            assertTrue(state.getReadOnly(-1));

            assertThrows(LuaError.class, () -> state.setField(-1, "world"));
        }

        //todo add more tests for rawset/rawget once i have metatable support
    }

    @Nested
    class FunctionValues {

        @Test
        void pushRead(LuaState state, Arena arena) {
            var func = new MockLuaFunc(arena);
            state.pushFunction(func.ref);

            assertEquals(LuaType.FUNCTION, state.type(-1));
            assertTrue(state.isFunction(-1));
            assertTrue(state.isNativeFunction(-1));
            assertFalse(state.isLuaFunction(-1));
        }

        @Test
        void emptyFunctionCall(LuaState state, Arena arena) {
            var func = new MockLuaFunc(arena);

            state.pushFunction(func.ref);
            state.call(0, 0);

            func.assertCalled(1);
        }

        @Test
        void functionParamReturn(LuaState state, Arena arena) {
            var func = LuaFunc.wrap(
                L -> {
                    boolean b = L.type(1) == LuaType.TABLE;
                    L.pushString(b ? "yes" : "no");
                    return 1;
                },
                "func",
                arena
            );

            state.pushFunction(func);
            state.pushValue(-1);

            state.newTable();
            state.call(1, 1);
            assertEquals("yes", state.toString(-1));
            state.pop(1);

            state.pushString("blah");
            state.call(1, 1);
            assertEquals("no", state.toString(-1));
        }

        private static class MockLuaFunc {

            private final AtomicInteger callCount = new AtomicInteger(0);
            public final LuaFunc ref;

            public MockLuaFunc(Arena arena) {
                this.ref = LuaFunc.wrap(
                    _ -> {
                        callCount.incrementAndGet();
                        return 0;
                    },
                    "mockFunc",
                    arena
                );
            }

            public void assertCalled() {
                assertEquals(1, callCount.get(), "was not called");
            }

            public void assertCalled(int times) {
                assertEquals(
                    times,
                    callCount.get(),
                    "was not called " + times + " times"
                );
            }
        }
    }

    @Nested
    class UserDataValues {

        record MyUserData(int value) {}

        @Test
        void pushReadUntagged(LuaState state) {
            var ud = new MyUserData(12345);
            state.newUserData(ud);
            assertTrue(state.isUserData(-1));
            assertSame(ud, state.toUserData(-1));
            assertEquals(0, state.userDataTag(-1));
        }

        @Test
        void setTagLater(LuaState state) {
            state.newUserData(new MyUserData(12345));
            assertEquals(0, state.userDataTag(-1));

            state.setUserDataTag(-1, 42);
            assertEquals(42, state.userDataTag(-1));
        }

        @Test
        void pushReadTagged(LuaState state) {
            var ud = new MyUserData(12345);
            state.newUserDataTagged(ud, 42);
            assertTrue(state.isUserData(-1));
            assertSame(ud, state.toUserData(-1));
            assertNull(state.toUserDataTagged(-1, 12));
            assertSame(ud, state.toUserDataTagged(-1, 42));
            assertEquals(42, state.userDataTag(-1));
        }

        @Test
        void pushReadTaggedWithMetatable(LuaState state) {
            state.newTable();
            state.pushString("MyType");
            state.setField(-2, "__type");
            state.setUserDataMetaTable(42);

            state.getUserDataMetaTable(42);
            state.getField(-1, "__type");
            assertEquals("MyType", state.toString(-1));
            state.pop(2);

            state.newUserDataTaggedWithMetatable(new MyUserData(12345), 42);
            assertTrue(state.toStringRepr(-1).startsWith("MyType"));
        }
    }

    @Nested
    class LightUserDataValues {

        @Test
        void pushRead(LuaState state) {
            state.pushLightUserData(12345);
            assertTrue(state.isLightUserData(-1));
            assertEquals(12345, state.toLightUserData(-1));
        }

        @Test
        void pushTagged(LuaState state) {
            state.pushLightUserDataTagged(12345, 123);
            assertTrue(state.isLightUserData(-1));
            assertEquals(12345, state.toLightUserData(-1));
            assertEquals(12345, state.toLightUserDataTagged(-1, 123));
            assertEquals(0, state.toLightUserDataTagged(-1, 321));
        }

        @Test
        void tagNames(LuaState state) {
            assertNull(state.getLightUserDataName(1));

            state.setLightUserDataName(123, "test");
            assertEquals("test", state.getLightUserDataName(123));

            state.pushLightUserDataTagged(12345, 123);
            assertEquals("test: 0x0000000000003039", state.toStringRepr(-1));
        }

        @Test
        void readNonLightUserData(LuaState state) {
            state.pushBoolean(true);
            assertEquals(0, state.toLightUserData(-1));
        }
    }

    @Nested
    class ThreadValues {

        @Test
        void abc() {
        }
    }

    @Test
    void gcCategoryManipulation(LuaState state) {
        // we should have allocated something by now while creating the state :)
        assertNotEquals(0, state.totalBytes(0));

        var s = LuaFunc.wrap(a -> 1, "ab");

        state.setMemCat(42);
        assertEquals(0, state.totalBytes(42));
        state.newUserData(
            "this shouldnt count, it should only be 8 bytes because java owns this string"
        );
        assertEquals(32, state.totalBytes(42));
    }

    @Test
    void threadData(LuaState state) {
        var obj = new Object();

        state.setThreadData(obj);
        assertSame(obj, state.getThreadData());

        state.setThreadData(new Object());
        assertNotSame(obj, state.getThreadData());

        state.setThreadData(null);
        assertNull(state.getThreadData());
    }

    //TODO test all the check and opt methods
}
