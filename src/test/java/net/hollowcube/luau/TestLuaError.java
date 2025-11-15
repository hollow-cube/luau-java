package net.hollowcube.luau;

import org.junit.jupiter.api.Test;

import java.lang.foreign.Arena;

import static net.hollowcube.luau.TestHelpers.*;
import static org.junit.jupiter.api.Assertions.*;

@LuaStateParam
public class TestLuaError {

    @Test
    void stripDefaultErrorText() {
        var input = "[string \"test.luau\"]:1: invalid argument #1 to 'errfunc' (MyType expected, got number)";
        var result = LuaStateImpl.stripDefaultErrorPrefix(input);

        assertEquals("invalid argument #1 to 'errfunc' (MyType expected, got number)", result);
    }

    @Test
    void errorShouldThrowLuaError(LuaState state) {
        assertThrows(LuaError.class, state::error);
    }

    @Test
    void errorShouldThrowLuaErrorWithMessage(LuaState state) {
        var err = assertThrows(LuaError.class, () -> state.error("test error"));
        assertEquals("test error", err.getMessage());
    }

    @Test
    void typeErrorShouldThrowLuaError(LuaState state) {
        var err = assertThrows(LuaError.class, () -> state.typeError(1, "MyType"));
        assertEquals("missing argument #1 (MyType expected)", err.getMessage());
    }

    @Test
    void typeErrorShouldThrowLuaError2(LuaState state) {
        state.pushString("hello");
        var err = assertThrows(LuaError.class, () -> state.typeError(-1, "MyType"));
        assertEquals("invalid argument #-1 (MyType expected, got string)", err.getMessage());
    }

    @Test
    void argErrorShouldThrowLuaError(LuaState state) {
        var err = assertThrows(LuaError.class, () -> state.argError(1, "test error"));
        assertEquals("invalid argument #1 (test error)", err.getMessage());
    }

    @Test
    void checkStackThrowLuaError(LuaState state) {
        var err = assertThrows(LuaError.class, () -> state.checkStack(Integer.MAX_VALUE, "test error"));
        assertEquals("stack overflow (test error)", err.getMessage());
    }

    @Test
    void checkStackThrowLuaError2(LuaState state) {
        var err = assertThrows(LuaError.class, () -> state.checkStack(Integer.MAX_VALUE, null));
        assertEquals("stack overflow", err.getMessage());
    }

    @Test
    void throwInUpcall(LuaState state, Arena arena) {
        var func = LuaFunc.wrap(L -> {
            L.error("test error");
            return 0;
        }, "errfunc", arena);

        state.pushFunction(func);
        var err = assertThrows(LuaError.class, () -> state.call(0, 0));
        assertEquals("test error", err.getMessage());
    }

    @Test
    void throwInUpcallNoMessage(LuaState state, Arena arena) {
        var func = LuaFunc.wrap(L -> {
            L.error();
            return 0;
        }, "errfunc", arena);

        state.pushFunction(func);
        var err = assertThrows(LuaError.class, () -> state.call(0, 0));
        assertNull(err.getMessage());
    }

    @Test
    void throwInUpcallViaNative(LuaState state, Arena arena) {
        var func = LuaFunc.wrap(L -> {
            // typeError ends up calling lua, so the exception starts there.
            L.typeError(1, "MyType");
            return 0;
        }, "errfunc", arena);

        state.pushFunction(func);
        state.pushInteger(1);
        var err = assertThrows(LuaError.class, () -> state.call(1, 0));
        assertEquals("invalid argument #1 to 'errfunc' (MyType expected, got number)", err.getMessage());
    }

    @Test
    void throwInUpcallThroughLuaWithTrace(LuaState state, Arena arena) {
        var func = LuaFunc.wrap(L -> {
            // typeError ends up calling lua, so the exception starts there.
            L.typeError(1, "MyType");
            return 0;
        }, "errfunc", arena);
        state.openLibs();

        state.pushFunction(func);
        state.setGlobal("myfunc");

        load(state, """
                myfunc(123)
                """);

        // TODO: new lua_rawgetptagged and lua_rawsetptagged functions

        var err = assertThrows(LuaError.class, () -> state.call(0, 0));
        // TODO: strip the default file name/line from the start, if present.
        assertMatches("""
                              net.hollowcube.luau.LuaError: invalid argument #1 to 'errfunc' (MyType expected, got number)
                              	at net.hollowcube.luau.LuaStateImpl.typeError(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$throwInUpcallThroughLuaWithTrace$0(TestLuaError.java:0)
                              	at lua.<anonymous>(test.luau:1)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$throwInUpcallThroughLuaWithTrace$1(TestLuaError.java:0)
                              """, printStackTrace(err, 6));
    }

    @Test
    void throwInLuaWithTrace(LuaState state, Arena arena) {
        state.openLibs();
        load(state, """
                error("test error")
                """);

        var err = assertThrows(LuaError.class, () -> state.call(0, 0));
        assertMatches("""
                              net.hollowcube.luau.LuaError: test error
                              	at lua.error(<native>)
                              	at lua.<anonymous>(test.luau:1)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$throwInLuaWithTrace$0(TestLuaError.java:0)
                              """, printStackTrace(err, 5));
    }

    // Another method to appear in the stacktrace
    private static void anotherMethod(LuaState L) {
        L.call(0, 0); // call the function on the stack
    }

    @Test
    void multiLevelErrorTraceback(LuaState state, Arena arena) {
        state.openLibs();

        var func1 = LuaFunc.wrap(L -> {
            // typeError ends up calling lua, so the exception starts there.
            L.typeError(1, "MyType");
            return 0;
        }, "func1", arena);
        state.pushFunction(func1);
        state.setGlobal("func1");

        var func2 = LuaFunc.wrap(L -> {
            anotherMethod(L);
            return 0;
        }, "func2", arena);
        state.pushFunction(func2);
        state.setGlobal("func2");

        load(state, """
                function myFunc()
                    func1(123)
                end
                
                func2(myFunc)
                """);

        var err = assertThrows(LuaError.class, () -> state.call(0, 0));
        assertMatches("""
                              net.hollowcube.luau.LuaError: invalid argument #1 to 'func1' (MyType expected, got number)
                              	at net.hollowcube.luau.LuaStateImpl.typeError(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$multiLevelErrorTraceback$0(TestLuaError.java:0)
                              	at lua.myFunc(test.luau:2)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.anotherMethod(TestLuaError.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$multiLevelErrorTraceback$1(TestLuaError.java:0)
                              	at lua.<anonymous>(test.luau:5)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$multiLevelErrorTraceback$2(TestLuaError.java:0)
                              """, printStackTrace(err, 10));
    }

    @Test
    void veryVeryDeepJavaStart(LuaState state, Arena arena) {
        state.openLibs();

        var func0 = LuaFunc.wrap(_ -> {
            try {
                //noinspection divzero,NumericOverflow,unused
                int i = 1 / 0;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return 0;
        }, "func0", arena);
        state.pushFunction(func0);
        state.setGlobal("func0");

        for (int i = 1; i <= 10; i++) {
            var func = LuaFunc.wrap(L -> {
                anotherMethod(L);
                return 0;
            }, "func" + i, arena);
            state.pushFunction(func);
            state.setGlobal("func" + i);
        }

        load(state, """
                function f1() func0() end
                function f2() func1(f1) end
                function f3() func2(f2) end
                function f4() func3(f3) end
                function f5() func4(f4) end
                function f6() func5(f5) end
                function f7() func6(f6) end
                function f8() func7(f7) end
                function f9() func8(f8) end
                function f10() func9(f9) end
                
                func10(f10);
                """);

        var err = assertThrows(LuaError.class, () -> state.call(0, 0));
        assertMatches("""
                              net.hollowcube.luau.LuaError: java.lang.RuntimeException: java.lang.ArithmeticException: / by zero
                              	at net.hollowcube.luau.TestLuaError.lambda$veryVeryDeepJavaStart$0(TestLuaError.java:0)
                              	at lua.f1(test.luau:1)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.anotherMethod(TestLuaError.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$veryVeryDeepJavaStart$1(TestLuaError.java:0)
                              	at lua.f2(test.luau:2)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.anotherMethod(TestLuaError.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$veryVeryDeepJavaStart$1(TestLuaError.java:0)
                              	at lua.f3(test.luau:3)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.anotherMethod(TestLuaError.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$veryVeryDeepJavaStart$1(TestLuaError.java:0)
                              	at lua.f4(test.luau:4)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.anotherMethod(TestLuaError.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$veryVeryDeepJavaStart$1(TestLuaError.java:0)
                              	at lua.f5(test.luau:5)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.anotherMethod(TestLuaError.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$veryVeryDeepJavaStart$1(TestLuaError.java:0)
                              	at lua.f6(test.luau:6)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.anotherMethod(TestLuaError.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$veryVeryDeepJavaStart$1(TestLuaError.java:0)
                              	at lua.f7(test.luau:7)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.anotherMethod(TestLuaError.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$veryVeryDeepJavaStart$1(TestLuaError.java:0)
                              	at lua.f8(test.luau:8)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.anotherMethod(TestLuaError.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$veryVeryDeepJavaStart$1(TestLuaError.java:0)
                              	at lua.f9(test.luau:9)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.anotherMethod(TestLuaError.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$veryVeryDeepJavaStart$1(TestLuaError.java:0)
                              	at lua.f10(test.luau:10)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.anotherMethod(TestLuaError.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$veryVeryDeepJavaStart$1(TestLuaError.java:0)
                              	at lua.<anonymous>(test.luau:12)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$veryVeryDeepJavaStart$2(TestLuaError.java:0)
                              	at org.junit.jupiter.api.AssertThrows.assertThrows(AssertThrows.java:0)
                              	at org.junit.jupiter.api.AssertThrows.assertThrows(AssertThrows.java:0)
                              	at org.junit.jupiter.api.Assertions.assertThrows(Assertions.java:0)
                              	at net.hollowcube.luau.TestLuaError.veryVeryDeepJavaStart(TestLuaError.java:0)
                              """, printStackTrace(err, 49));
    }

    @Test
    void luauStdlibErrorSource(LuaState state, Arena arena) {
        state.openLibs();

        var callThis = LuaFunc.wrap(L -> {
            L.call(0, 0);
            return 0;
        }, "callThis", arena);
        state.pushFunction(callThis);
        state.setGlobal("callThis");

        load(state, """
                function myFunc()
                    local t = { 1, 2, 3 }
                    table.foreach(t, function()
                        coroutine.yield()
                    end)
                end
                
                callThis(myFunc)
                """);

        var err = assertThrows(LuaError.class, () -> state.call(0, 0));
        assertMatches("""
                              net.hollowcube.luau.LuaError: attempt to yield across metamethod/C-call boundary
                              	at lua.yield(<native>)
                              	at lua.<anonymous>(test.luau:4)
                              	at lua.foreach(<native>)
                              	at lua.myFunc(test.luau:3)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$luauStdlibErrorSource$0(TestLuaError.java:0)
                              	at lua.<anonymous>(test.luau:8)
                              	at net.hollowcube.luau.LuaStateImpl.call(LuaStateImpl.java:0)
                              	at net.hollowcube.luau.TestLuaError.lambda$luauStdlibErrorSource$1(TestLuaError.java:0)
                              """, printStackTrace(err, 10));
    }

}
