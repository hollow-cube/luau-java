package net.hollowcube.luau.compiler;

import net.hollowcube.luau.internal.compiler.lua_CompileOptions;
import net.hollowcube.luau.internal.compiler.luacode_h;
import net.hollowcube.luau.util.NativeLibraryLoader;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static net.hollowcube.luau.internal.compiler.luacode_h.luau_ext_free;

@SuppressWarnings("preview")
record LuauCompilerImpl(
        @NotNull OptimizationLevel optimizationLevel,
        @NotNull DebugLevel debugLevel,
        @NotNull TypeInfoLevel typeInfoLevel,
        @NotNull CoverageLevel coverageLevel,
        @Nullable String vectorLib,
        @Nullable String vectorCtor,
        @Nullable String vectorType,
        @NotNull List<String> mutableGlobals,
        @NotNull List<String> userdataTypes
) implements LuauCompiler {
    static {
        NativeLibraryLoader.loadLibrary("compiler");
    }

    @Override
    public byte[] compile(byte @NotNull [] source) throws LuauCompileException {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment sourceStr = arena.allocateFrom(ValueLayout.JAVA_BYTE, source);
            final MemorySegment bytecodeSize = arena.allocate(ValueLayout.JAVA_LONG);
            final MemorySegment compileOpts = createCompileOptions(arena);

            final MemorySegment result = luacode_h.luau_compile(sourceStr, source.length, compileOpts, bytecodeSize);
            final long length = bytecodeSize.get(ValueLayout.JAVA_LONG, 0);
            final byte[] bytecode = result.asSlice(0, length).toArray(ValueLayout.JAVA_BYTE);
            luau_ext_free(result);

            // Bytecode now contains either an error or valid luau bytecode.
            // A zero in the first byte indicates that the rest is an error.
            assert bytecode.length > 0;
            if (bytecode[0] != 0) return bytecode; // Compiled successfully, great!

            // There was an error.
            final String errorMessage = new String(bytecode, 1, bytecode.length - 1, StandardCharsets.UTF_8);
            throw new LuauCompileException(errorMessage);
        }
    }

    private @NotNull MemorySegment createCompileOptions(Arena arena) {
        final MemorySegment opts = lua_CompileOptions.allocate(arena);
        lua_CompileOptions.optimizationLevel(opts, optimizationLevel.ordinal());
        lua_CompileOptions.debugLevel(opts, debugLevel.ordinal());
        lua_CompileOptions.typeInfoLevel(opts, typeInfoLevel.ordinal());
        lua_CompileOptions.coverageLevel(opts, coverageLevel.ordinal());
        if (vectorLib != null) lua_CompileOptions.vectorLib(opts, arena.allocateFrom(vectorLib));
        if (vectorCtor != null) lua_CompileOptions.vectorCtor(opts, arena.allocateFrom(vectorCtor));
        if (vectorType != null) lua_CompileOptions.vectorType(opts, arena.allocateFrom(vectorType));
        if (!mutableGlobals.isEmpty()) {
            // size + 1 because the array is null terminated.
            final MemorySegment mutableGlobals = arena.allocate(ValueLayout.ADDRESS, this.mutableGlobals.size() + 1);
            for (int i = 0; i < this.mutableGlobals.size(); i++) {
                final MemorySegment str = arena.allocateFrom(this.mutableGlobals.get(i));
                mutableGlobals.setAtIndex(ValueLayout.ADDRESS, i, str);
            }
            lua_CompileOptions.mutableGlobals(opts, mutableGlobals);
        }
        if (!userdataTypes.isEmpty()) {
            // size + 1 because the array is null terminated.
            final MemorySegment userdataTypes = arena.allocate(ValueLayout.ADDRESS, this.userdataTypes.size() + 1);
            for (int i = 0; i < this.userdataTypes.size(); i++) {
                final MemorySegment str = arena.allocateFrom(this.userdataTypes.get(i));
                userdataTypes.setAtIndex(ValueLayout.ADDRESS, i, str);
            }
            lua_CompileOptions.userdataTypes(opts, userdataTypes);
        }
        return opts;
    }

    static final class BuilderImpl implements Builder {
        private OptimizationLevel optimizationLevel = OptimizationLevel.BASELINE;
        private DebugLevel debugLevel = DebugLevel.BACKTRACE;
        private TypeInfoLevel typeInfoLevel = TypeInfoLevel.NATIVE_MODULES;
        private CoverageLevel coverageLevel = CoverageLevel.NONE;
        private String vectorLib = null;
        private String vectorCtor = null;
        private String vectorType = null;
        private List<String> mutableGlobals = List.of();
        private List<String> userdataTypes = List.of();

        @Override
        public @NotNull Builder optimizationLevel(@NotNull OptimizationLevel level) {
            this.optimizationLevel = level;
            return this;
        }

        @Override
        public @NotNull Builder debugLevel(@NotNull DebugLevel level) {
            this.debugLevel = level;
            return this;
        }

        @Override
        public @NotNull Builder typeInfoLevel(@NotNull TypeInfoLevel level) {
            this.typeInfoLevel = level;
            return this;
        }

        @Override
        public @NotNull Builder coverageLevel(@NotNull CoverageLevel level) {
            this.coverageLevel = level;
            return this;
        }

        @Override
        public @NotNull Builder vectorLib(@NotNull String vectorLib) {
            this.vectorLib = vectorLib;
            return this;
        }

        @Override
        public @NotNull Builder vectorCtor(@NotNull String vectorCtor) {
            this.vectorCtor = vectorCtor;
            return this;
        }

        @Override
        public @NotNull Builder vectorType(@NotNull String vectorType) {
            this.vectorType = vectorType;
            return this;
        }

        @Override
        public @NotNull Builder mutableGlobals(@NotNull List<String> mutableGlobals) {
            this.mutableGlobals = mutableGlobals;
            return this;
        }

        @Override
        public @NotNull Builder userdataTypes(@NotNull List<String> userdataTypes) {
            this.userdataTypes = userdataTypes;
            return this;
        }

        @Override
        public @NotNull LuauCompiler build() {
            return new LuauCompilerImpl(
                    optimizationLevel, debugLevel,
                    typeInfoLevel, coverageLevel,
                    vectorLib, vectorCtor,
                    vectorType, mutableGlobals,
                    userdataTypes
            );
        }
    }

}
