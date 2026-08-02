package net.hollowcube.luau;

import static net.hollowcube.luau.TestHelpers.eval;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// The `__namecall` metamethod and the atom accessors which are only valid inside it.
///
/// Luau compiles `obj:method(...)` to a NAMECALL instruction. For userdata that dispatches
/// through `__namecall` when the metatable has one, which is the whole point of the
/// optimisation: the method name never becomes a Lua string, so the handler has to ask for
/// it with [LuaState#nameCallAtom()] - the only place that call is legal. Note this does
/// *not* apply to tables, see [Dispatch#tableNameCallIsAnOrdinaryLookup].
@LuaStateParam
class TestLuaNameCall {

    /// Leaves a userdata with a `__namecall` metatable on the stack and binds it to the
    /// global `obj`. The metatable is left at index -1 below it so callers can add more
    /// metamethods.
    private static void pushObjectWithNameCall(
            LuaState state,
            Arena arena,
            ToIntFunction<LuaState> handler
    ) {
        state.newUserData(new Object());

        state.newTable();
        state.pushFunction(LuaFunc.wrap(handler, "__namecall", arena));
        state.setField(-2, "__namecall");
        state.pushValue(-1);
        state.setMetaTable(-3);
        state.setGlobal("mt");

        state.pushValue(-1);
        state.setGlobal("obj");
    }

    @Nested
    class WithoutAtoms {

        /// With no user atom callback installed every name comes back as a plain string.
        @Test
        void nameCallAtomReportsTheMethodName(LuaState state, Arena arena) {
            final List<String> seen = new ArrayList<>();

            pushObjectWithNameCall(state, arena, s -> {
                var name = assertInstanceOf(LuaString.Str.class, s.nameCallAtom());
                seen.add(name.str());
                return 0;
            });

            eval(state, """
                obj:hello()
                obj:goodbye()
                """);

            assertEquals(List.of("hello", "goodbye"), seen);
        }

        @Test
        void nameCallAtomRawIsUnsetWithoutACallback(LuaState state, Arena arena) {
            final List<Short> seen = new ArrayList<>();

            pushObjectWithNameCall(state, arena, s -> {
                seen.add(s.nameCallAtomRaw());
                return 0;
            });

            eval(state, "obj:hello()");

            assertEquals(List.of(LuaState.NO_ATOM), seen);
        }
    }

    @Nested
    class WithAtoms {

        /// The same callback which assigns string atoms is what makes namecall atoms
        /// available, so a handler can switch on a short instead of comparing strings.
        @Test
        void nameCallAtomIsTheRegisteredAtom(LuaState state, Arena arena) {
            state.callbacks().userAtom(LuaCallbacks.UserAtom.allocate(
                    (_, str) -> switch (str) {
                        case "hello" -> 7;
                        case "goodbye" -> 9;
                        default -> LuaState.NO_ATOM;
                    },
                    arena
            ));

            final List<Short> raw = new ArrayList<>();
            final List<LuaString> resolved = new ArrayList<>();

            pushObjectWithNameCall(state, arena, s -> {
                raw.add(s.nameCallAtomRaw());
                resolved.add(s.nameCallAtom());
                return 0;
            });

            eval(state, """
                obj:hello()
                obj:goodbye()
                obj:unregistered()
                """);

            assertEquals(List.of((short) 7, (short) 9, LuaState.NO_ATOM), raw);
            assertEquals(
                    List.of(
                            new LuaString.Atom((short) 7),
                            new LuaString.Atom((short) 9),
                            new LuaString.Str("unregistered")
                    ),
                    resolved
            );
        }
    }

    @Nested
    class Dispatch {

        /// The receiver is argument 1 and the call arguments follow it; the method name is
        /// not on the stack at all.
        @Test
        void receiverAndArgumentsArePassedThrough(LuaState state, Arena arena) {
            pushObjectWithNameCall(state, arena, s -> {
                assertEquals(3, s.top());
                assertTrue(s.isUserData(1), "the receiver is argument 1");
                assertEquals("a", s.checkString(2));
                assertEquals(2L, s.checkInteger(3));

                var name = assertInstanceOf(LuaString.Str.class, s.nameCallAtom());
                s.pushString(name.str());
                return 1;
            });

            eval(state, """
                return obj:concat("a", 2)
                """, 1);

            assertEquals("concat", state.toString(-1));
        }

        /// __namecall takes precedence over __index for the `obj:method()` form.
        @Test
        void nameCallWinsOverIndex(LuaState state, Arena arena) {
            pushObjectWithNameCall(state, arena, s -> {
                s.pushString("from namecall");
                return 1;
            });

            eval(state, """
                mt.__index = function(_, _)
                    return function() return "from index" end
                end
                return obj:thing()
                """, 1);

            assertEquals("from namecall", state.toString(-1));
        }

        /// A plain field lookup is not a namecall, so it still goes through __index.
        @Test
        void indexIsStillUsedForNonCallSyntax(LuaState state, Arena arena) {
            pushObjectWithNameCall(state, arena, _ -> 0);

            eval(state, """
                mt.__index = function(_, key)
                    return "indexed:" .. key
                end
                return obj.thing
                """, 1);

            assertEquals("indexed:thing", state.toString(-1));
        }

        /// Tables never dispatch through __namecall - `t:method()` is an ordinary lookup of
        /// `method` on the table, so a __namecall on a table's metatable is dead weight.
        @Test
        void tableNameCallIsAnOrdinaryLookup(LuaState state, Arena arena) {
            state.newTable();

            state.newTable();
            state.pushFunction(LuaFunc.wrap(_ -> {
                throw new AssertionError("__namecall must not fire for a table");
            }, "__namecall", arena));
            state.setField(-2, "__namecall");
            state.setMetaTable(-2);
            state.setGlobal("t");

            var err = assertThrows(LuaError.class, () -> eval(state, "t:hello()"));
            assertEquals("attempt to call missing method 'hello' of table", err.getMessage());
        }
    }

    @Nested
    class OutsideNameCall {

        @Test
        void nameCallAtomThrowsOutsideAMetamethod(LuaState state) {
            var err = assertThrows(IllegalStateException.class, state::nameCallAtom);
            assertEquals(
                    "namecallatom may only be called within a __namecall metamethod",
                    err.getMessage()
            );
        }

        @Test
        void nameCallAtomRawIsUnsetOutsideAMetamethod(LuaState state) {
            assertEquals(LuaState.NO_ATOM, state.nameCallAtomRaw());
        }

        /// An ordinary Java function called as `plain()` is not a namecall either.
        @Test
        void plainFunctionCallIsNotANameCall(LuaState state, Arena arena) {
            state.pushFunction(LuaFunc.wrap(s -> {
                assertThrows(IllegalStateException.class, s::nameCallAtom);
                assertEquals(LuaState.NO_ATOM, s.nameCallAtomRaw());
                return 0;
            }, "plain", arena));
            state.setGlobal("plain");

            eval(state, "plain()");
        }
    }
}
