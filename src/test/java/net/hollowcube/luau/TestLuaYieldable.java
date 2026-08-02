package net.hollowcube.luau;

import static net.hollowcube.luau.TestHelpers.eval;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import org.junit.jupiter.api.Test;

/// A Java frame cannot be suspended, so a Java function which wants to protected-call Lua
/// code that may yield has to hand the VM a continuation to re-enter through.
@LuaStateParam
class TestLuaYieldable {

    /// `tryCall(f)` -> `ok, resultOrError`, like pcall but tolerating a yield inside `f`.
    private static void installTryCall(LuaState state, Arena arena) {
        state.pushFunction(LuaFunc.yieldable(
                s -> {
                    s.pushValue(1);
                    return s.pcallYieldable(0, 1);
                },
                (s, status) -> {
                    s.pushBoolean(status == LuaStatus.OK);
                    s.insert(-2);
                    return 2;
                },
                "tryCall", arena));
        state.setGlobal("tryCall");
    }

    @Test
    void completesSynchronously(LuaState state, Arena arena) {
        state.openLibs();
        installTryCall(state, arena);

        eval(state, "return tryCall(function() return 'value' end)", 2);

        assertTrue(state.toBoolean(-2));
        assertEquals("value", state.toString(-1));
        state.pop(2);
    }

    /// The error never surfaces in Java: luaL_pcallyieldable catches it and reports it to
    /// the continuation as a status, with the error value on the stack.
    @Test
    void reportsErrorsToTheContinuation(LuaState state, Arena arena) {
        state.openLibs();
        installTryCall(state, arena);

        eval(state, "return tryCall(function() error('boom') end)", 2);

        assertEquals(false, state.toBoolean(-2));
        assertTrue(state.toStringRepr(-1).endsWith("boom"), state.toStringRepr(-1));
        state.pop(2);
    }

    /// The whole point: the callback yields, the Java frame unwinds, and the continuation
    /// runs on the later resume with the value passed back in.
    @Test
    void survivesAYield(LuaState state, Arena arena) {
        state.openLibs();
        installTryCall(state, arena);

        eval(state, """
                local co = coroutine.create(function()
                    return tryCall(function()
                        local resumed = coroutine.yield("waiting")
                        return resumed .. "!"
                    end)
                end)

                local _, waiting = coroutine.resume(co)
                local _, ok, value = coroutine.resume(co, "resumed")
                return waiting, ok, value
                """, 3);

        assertEquals("waiting", state.toString(-3));
        assertTrue(state.toBoolean(-2));
        assertEquals("resumed!", state.toString(-1));
        state.pop(3);
    }

    /// An error raised after the yield still reaches the continuation, which is what makes
    /// this a real pcall replacement rather than only a call.
    @Test
    void reportsErrorsRaisedAfterAYield(LuaState state, Arena arena) {
        state.openLibs();
        installTryCall(state, arena);

        eval(state, """
                local co = coroutine.create(function()
                    return tryCall(function()
                        coroutine.yield()
                        error("late")
                    end)
                end)

                coroutine.resume(co)
                local _, ok, err = coroutine.resume(co)
                return ok, err
                """, 2);

        assertEquals(false, state.toBoolean(-2));
        assertTrue(state.toStringRepr(-1).endsWith("late"), state.toStringRepr(-1));
        state.pop(2);
    }

    /// A continuation which fails raises from the trampoline once the Java frame is gone,
    /// so it propagates like any other error out of a Java function.
    @Test
    void propagatesContinuationFailure(LuaState state, Arena arena) {
        state.openLibs();
        state.pushFunction(LuaFunc.yieldable(
                s -> {
                    s.pushValue(1);
                    return s.pcallYieldable(0, 0);
                },
                (s, _) -> {
                    throw s.error("continuation failed");
                },
                "run", arena));
        state.setGlobal("run");

        final LuaError error = assertThrows(LuaError.class,
                () -> eval(state, "run(function() end)"));
        assertEquals("continuation failed", error.getMessage());
    }

    /// Without a continuation there is nothing to resume into, and upstream only asserts
    /// on this - it would call through a null pointer in a release build. The
    /// IllegalStateException is raised inside a Java function, so it reaches the caller as
    /// a Lua error like any other.
    @Test
    void rejectsUseWithoutAContinuation(LuaState state, Arena arena) {
        state.openLibs();
        state.pushFunction(LuaFunc.wrap(s -> {
            s.pushValue(1);
            return s.pcallYieldable(0, 0);
        }, "notYieldable", arena));
        state.setGlobal("notYieldable");

        final LuaError error = assertThrows(LuaError.class,
                () -> eval(state, "notYieldable(function() end)"));
        assertTrue(error.getMessage().contains("LuaFunc.yieldable"), error.getMessage());
    }
}
