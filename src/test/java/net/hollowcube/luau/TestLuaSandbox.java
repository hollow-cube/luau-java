package net.hollowcube.luau;

import static net.hollowcube.luau.TestHelpers.eval;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// Sandboxing is entirely built out of the readonly flag: `luaL_sandbox` freezes the globals
/// table and every library table hanging off it, and `luaL_sandboxthread` gives a thread a
/// fresh writable globals table which reads through to the frozen one.
///
/// Note that readonly is only enforced against Lua code - the host can always turn it back
/// off, which is what makes the setup order (open libraries, then sandbox) matter.
@LuaStateParam
class TestLuaSandbox {

    private static final int GLOBALS_INDEX = LuaStateImpl.GLOBALS_INDEX;

    @Nested
    class Sandbox {

        @Test
        void globalsBecomeReadOnly(LuaState state) {
            state.openLibs();
            assertFalse(state.getReadOnly(GLOBALS_INDEX));

            state.sandbox();

            assertTrue(state.getReadOnly(GLOBALS_INDEX));
        }

        @Test
        void libraryTablesBecomeReadOnly(LuaState state) {
            state.openLibs();
            state.getGlobal("string");
            assertFalse(state.getReadOnly(-1));
            state.pop(1);

            state.sandbox();

            state.getGlobal("string");
            assertTrue(state.getReadOnly(-1));
            state.getGlobal("table");
            assertTrue(state.getReadOnly(-1));
        }

        /// Tables created after the sandbox call are untouched, sandboxing is a one shot
        /// sweep rather than a mode the VM stays in.
        @Test
        void newTablesAreStillWritable(LuaState state) {
            state.openLibs();
            state.sandbox();

            state.newTable();
            assertFalse(state.getReadOnly(-1));
            state.pushString("value");
            state.setField(-2, "key");
            assertEquals(LuaType.STRING, state.rawGetField(-1, "key"));
        }

        @Test
        void writingAGlobalFromLuaFails(LuaState state) {
            state.openLibs();
            state.sandbox();

            final LuaError error = assertThrows(LuaError.class, () -> eval(state, "x = 1"));
            assertEquals("attempt to modify a readonly table", error.getMessage());
        }

        @Test
        void replacingALibraryFromLuaFails(LuaState state) {
            state.openLibs();
            state.sandbox();

            final LuaError error = assertThrows(LuaError.class,
                    () -> eval(state, "string.rep = nil"));
            assertEquals("attempt to modify a readonly table", error.getMessage());
        }

        @Test
        void readingGlobalsStillWorks(LuaState state) {
            state.openLibs();
            state.sandbox();

            eval(state, "return string.rep('ab', 2)", 1);
            assertEquals("abab", state.toString(-1));
        }

        /// The host is subject to the same flag, since setGlobal is an ordinary table write.
        @Test
        void writingAGlobalFromJavaAlsoFails(LuaState state) {
            state.openLibs();
            state.sandbox();

            state.pushInteger(1);
            final LuaError error = assertThrows(LuaError.class, () -> state.setGlobal("x"));
            assertEquals("attempt to modify a readonly table", error.getMessage());
        }

        /// ... but the host can lift the flag again, so the sandbox is a guard against the
        /// scripts rather than against the embedder.
        @Test
        void hostCanLiftTheFlagAgain(LuaState state) {
            state.openLibs();
            state.sandbox();

            state.setReadOnly(GLOBALS_INDEX, false);
            assertFalse(state.getReadOnly(GLOBALS_INDEX));

            state.pushInteger(1);
            state.setGlobal("x");
            eval(state, "return x", 1);
            assertEquals(1, state.toInteger(-1));
        }
    }

    @Nested
    class SandboxThread {

        @Test
        void threadGetsItsOwnWritableGlobals(LuaState state) {
            state.openLibs();
            state.sandbox();

            final LuaState thread = state.newThread();
            thread.sandboxThread();

            assertTrue(state.getReadOnly(GLOBALS_INDEX), "the parent stays frozen");
            assertFalse(thread.getReadOnly(GLOBALS_INDEX), "the thread gets a fresh table");
        }

        @Test
        void threadReadsThroughToTheParentGlobals(LuaState state) {
            state.openLibs();
            state.pushString("inherited");
            state.setGlobal("marker");
            state.sandbox();

            final LuaState thread = state.newThread();
            thread.sandboxThread();

            thread.getGlobal("marker");
            assertEquals("inherited", thread.toString(-1));

            eval(thread, "return marker .. '!'", 1);
            assertEquals("inherited!", thread.toString(-1));
        }

        @Test
        void threadWritesDoNotLeakBackToTheParent(LuaState state) {
            state.openLibs();
            state.sandbox();

            final LuaState thread = state.newThread();
            thread.sandboxThread();

            eval(thread, """
                    x = 'fromThread'
                    return x
                    """, 1);
            assertEquals("fromThread", thread.toString(-1));

            thread.getGlobal("x");
            assertEquals("fromThread", thread.toString(-1));

            state.getGlobal("x");
            assertTrue(state.isNil(-1), "the parent never sees the thread's global");
        }

        /// Each sandboxed thread gets its own table, so siblings are isolated from each other
        /// as well as from the parent.
        @Test
        void sandboxedThreadsAreIsolatedFromEachOther(LuaState state) {
            state.openLibs();
            state.sandbox();

            final LuaState a = state.newThread();
            a.sandboxThread();
            final LuaState b = state.newThread();
            b.sandboxThread();

            eval(a, "x = 'a'");

            b.getGlobal("x");
            assertTrue(b.isNil(-1));
            a.getGlobal("x");
            assertEquals("a", a.toString(-1));
        }

        /// The proxy metatable is frozen too, so a script cannot repoint __index at something
        /// of its own choosing.
        @Test
        void theProxyMetaTableIsReadOnly(LuaState state) {
            state.openLibs();
            state.sandbox();

            final LuaState thread = state.newThread();
            thread.sandboxThread();

            assertTrue(thread.getMetaTable(GLOBALS_INDEX));
            assertTrue(thread.getReadOnly(-1));
            assertEquals(LuaType.TABLE, thread.getField(-1, "__index"));
            assertTrue(thread.getReadOnly(-1), "__index is the parent's frozen globals table");
        }

        /// Without the parent being sandboxed first the thread is still isolated for writes,
        /// but the table it reads through to is not protected, so this is not a sandbox.
        @Test
        void doesNotFreezeTheParentByItself(LuaState state) {
            state.openLibs();

            final LuaState thread = state.newThread();
            thread.sandboxThread();

            assertFalse(state.getReadOnly(GLOBALS_INDEX));
            assertFalse(thread.getReadOnly(GLOBALS_INDEX));

            state.pushString("late");
            state.setGlobal("marker");
            thread.getGlobal("marker");
            assertEquals("late", thread.toString(-1), "reads still go through to the parent");
        }
    }
}
