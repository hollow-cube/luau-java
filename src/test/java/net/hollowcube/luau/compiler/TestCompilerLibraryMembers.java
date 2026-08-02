package net.hollowcube.luau.compiler;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static net.hollowcube.luau.internal.compiler.luaujavac_h.luau_ext_free;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.LuaStateParam;
import net.hollowcube.luau.LuaType;
import net.hollowcube.luau.internal.compiler.lua_CompileOptions;
import net.hollowcube.luau.internal.compiler.lua_LibraryMemberConstantCallback;
import net.hollowcube.luau.internal.compiler.lua_LibraryMemberTypeCallback;
import net.hollowcube.luau.internal.compiler.luacode_h;
import org.junit.jupiter.api.Test;

/// The two library member callbacks are compile time upcalls: Luau asks the host what
/// `library.member` means while it is folding constants and building type information.
///
/// [LuauCompiler.Builder] exposes neither them nor `librariesWithKnownMembers`, so these
/// tests drive `luau_compile` through the generated bindings directly. They exist mainly to
/// catch a silent break in the upcall signatures - a wrong descriptor here shows up as a
/// crash or as garbage library/member names rather than as a compile error.
@LuaStateParam
class TestCompilerLibraryMembers {

    /// A `LuauBytecodeType` from Bytecode.h.
    private static final int LBC_TYPE_NUMBER = 2;
    private static final int LBC_TYPE_STRING = 3;
    private static final int LBC_TYPE_ANY = 15;

    static {
        // Loads the native library (the static initializer lives on the compiler impl).
        LuauCompiler.builder().build();
    }

    private record Member(String library, String member) {}

    private static String string(MemorySegment cstr) {
        return cstr.reinterpret(Long.MAX_VALUE).getString(0);
    }

    private static MemorySegment strings(Arena arena, String... values) {
        // Null terminated, as the compiler walks it until it sees a null pointer.
        final MemorySegment array = arena.allocate(ADDRESS, values.length + 1);
        for (int i = 0; i < values.length; i++)
            array.setAtIndex(ADDRESS, i, arena.allocateFrom(values[i]));
        return array;
    }

    /// Compiles with a hand built [lua_CompileOptions], as the public builder cannot
    /// configure the library member callbacks.
    private static byte[] compile(
        Arena arena,
        String source,
        Consumer<MemorySegment> configure
    ) {
        final byte[] raw = source.getBytes(StandardCharsets.UTF_8);
        final MemorySegment sourceStr = arena.allocateFrom(JAVA_BYTE, raw);
        final MemorySegment bytecodeSize = arena.allocate(JAVA_LONG);

        final MemorySegment opts = lua_CompileOptions.allocate(arena);
        lua_CompileOptions.optimizationLevel(opts, 2); // folding needs the highest level
        lua_CompileOptions.debugLevel(opts, 1);
        lua_CompileOptions.typeInfoLevel(opts, 1); // all modules
        lua_CompileOptions.coverageLevel(opts, 0);
        lua_CompileOptions.vectorPrecision(opts, 0);
        configure.accept(opts);

        final MemorySegment result = luacode_h.luau_compile(
            sourceStr,
            raw.length,
            opts,
            bytecodeSize
        );
        final long length = bytecodeSize.get(JAVA_LONG, 0);
        final byte[] bytecode = result.asSlice(0, length).toArray(JAVA_BYTE);
        luau_ext_free(result);

        if (bytecode[0] == 0) throw new AssertionError(
            new String(bytecode, 1, bytecode.length - 1, StandardCharsets.UTF_8)
        );
        return bytecode;
    }

    private static void run(LuaState state, byte[] bytecode, int nret) {
        state.load("test.luau", bytecode);
        state.call(0, nret);
    }

    //region constant callback

    @Test
    void constantCallbackIsInvokedForKnownLibraryMembers(Arena arena) {
        final List<Member> seen = new ArrayList<>();

        compile(arena, "return mylib.answer + mylib.other", opts -> {
            lua_CompileOptions.librariesWithKnownMembers(opts, strings(arena, "mylib"));
            lua_CompileOptions.libraryMemberConstantCb(
                opts,
                lua_LibraryMemberConstantCallback.allocate(
                    (library, member, constant) ->
                        seen.add(new Member(string(library), string(member))),
                    arena
                )
            );
        });

        assertEquals(
            List.of(new Member("mylib", "answer"), new Member("mylib", "other")),
            seen
        );
    }

    /// A constant handed back by the callback is baked into the chunk, so the member does
    /// not have to exist at runtime at all.
    @Test
    void numberConstantIsSubstitutedIntoTheBytecode(LuaState state, Arena arena) {
        final byte[] bytecode = compile(arena, "return mylib.answer", opts -> {
            lua_CompileOptions.librariesWithKnownMembers(opts, strings(arena, "mylib"));
            lua_CompileOptions.libraryMemberConstantCb(
                opts,
                lua_LibraryMemberConstantCallback.allocate(
                    (library, member, constant) ->
                        luacode_h.luau_set_compile_constant_number(constant, 42),
                    arena
                )
            );
        });

        run(state, bytecode, 1);

        assertEquals(42, state.toInteger(-1));
    }

    @Test
    void stringConstantIsSubstitutedIntoTheBytecode(LuaState state, Arena arena) {
        final MemorySegment value = arena.allocateFrom("substituted");
        final byte[] bytecode = compile(arena, "return mylib.name", opts -> {
            lua_CompileOptions.librariesWithKnownMembers(opts, strings(arena, "mylib"));
            lua_CompileOptions.libraryMemberConstantCb(
                opts,
                lua_LibraryMemberConstantCallback.allocate(
                    (library, member, constant) ->
                        luacode_h.luau_set_compile_constant_string(
                            constant,
                            value,
                            "substituted".length()
                        ),
                    arena
                )
            );
        });

        run(state, bytecode, 1);

        assertEquals(LuaType.STRING, state.type(-1));
        assertEquals("substituted", state.toString(-1));
    }

    @Test
    void vectorConstantIsSubstitutedIntoTheBytecode(LuaState state, Arena arena) {
        final byte[] bytecode = compile(arena, "return mylib.up", opts -> {
            lua_CompileOptions.librariesWithKnownMembers(opts, strings(arena, "mylib"));
            lua_CompileOptions.libraryMemberConstantCb(
                opts,
                lua_LibraryMemberConstantCallback.allocate(
                    (library, member, constant) ->
                        luacode_h.luau_set_compile_constant_vector(constant, 0, 1, 0, 0),
                    arena
                )
            );
        });

        run(state, bytecode, 1);

        assertEquals(LuaType.VECTOR, state.type(-1));
        assertArrayEquals(new float[] { 0, 1, 0 }, state.toVector(-1));
    }

    /// Leaving the constant untouched means "I do not know", and the access is compiled as
    /// an ordinary global field read.
    @Test
    void anUntouchedConstantLeavesTheAccessAlone(LuaState state, Arena arena) {
        final byte[] substituted = compile(arena, "return mylib.answer", opts -> {
            lua_CompileOptions.librariesWithKnownMembers(opts, strings(arena, "mylib"));
            lua_CompileOptions.libraryMemberConstantCb(
                opts,
                lua_LibraryMemberConstantCallback.allocate(
                    (library, member, constant) ->
                        luacode_h.luau_set_compile_constant_number(constant, 42),
                    arena
                )
            );
        });
        final byte[] untouched = compile(arena, "return mylib.answer", opts -> {
            lua_CompileOptions.librariesWithKnownMembers(opts, strings(arena, "mylib"));
            lua_CompileOptions.libraryMemberConstantCb(
                opts,
                lua_LibraryMemberConstantCallback.allocate(
                    (library, member, constant) -> {},
                    arena
                )
            );
        });

        assertFalse(
            Arrays.equals(substituted, untouched),
            "the substituted constant did not change the bytecode"
        );

        state.newTable();
        state.pushNumber(7);
        state.setField(-2, "answer");
        state.setGlobal("mylib");

        run(state, untouched, 1);
        assertEquals(7, state.toInteger(-1));
    }

    /// The library has to be declared, otherwise the compiler never asks about it.
    @Test
    void undeclaredLibrariesAreNotOfferedToTheCallback(Arena arena) {
        final List<Member> seen = new ArrayList<>();

        compile(arena, "return otherlib.answer", opts ->
            lua_CompileOptions.libraryMemberConstantCb(
                opts,
                lua_LibraryMemberConstantCallback.allocate(
                    (library, member, constant) ->
                        seen.add(new Member(string(library), string(member))),
                    arena
                )
            ));

        assertEquals(List.of(), seen);
    }

    //endregion

    //region type callback

    @Test
    void typeCallbackIsInvokedForGlobalMembers(Arena arena) {
        final List<Member> seen = new ArrayList<>();

        compile(arena, """
            local function f()
                return mylib.answer
            end
            return f()
            """, opts ->
            lua_CompileOptions.libraryMemberTypeCb(
                opts,
                lua_LibraryMemberTypeCallback.allocate(
                    (library, member) -> {
                        seen.add(new Member(string(library), string(member)));
                        return LBC_TYPE_NUMBER;
                    },
                    arena
                )
            ));

        assertTrue(
            seen.contains(new Member("mylib", "answer")),
            "expected the callback to be asked about mylib.answer, got " + seen
        );
    }

    /// The reported type feeds the emitted type information, so answering something other
    /// than "any" is visible in the bytecode.
    @Test
    void reportedTypeChangesTheEmittedTypeInfo(Arena arena) {
        final String source = """
            local function f()
                local value = mylib.answer
                return value
            end
            return f()
            """;

        final byte[] any = compile(arena, source, opts ->
            lua_CompileOptions.libraryMemberTypeCb(
                opts,
                lua_LibraryMemberTypeCallback.allocate(
                    (library, member) -> LBC_TYPE_ANY,
                    arena
                )
            ));
        final byte[] number = compile(arena, source, opts ->
            lua_CompileOptions.libraryMemberTypeCb(
                opts,
                lua_LibraryMemberTypeCallback.allocate(
                    (library, member) -> LBC_TYPE_NUMBER,
                    arena
                )
            ));
        final byte[] string = compile(arena, source, opts ->
            lua_CompileOptions.libraryMemberTypeCb(
                opts,
                lua_LibraryMemberTypeCallback.allocate(
                    (library, member) -> LBC_TYPE_STRING,
                    arena
                )
            ));

        assertFalse(Arrays.equals(any, number), "LBC_TYPE_NUMBER was not recorded");
        assertFalse(Arrays.equals(number, string), "the reported type was ignored");
    }

    //endregion
}
