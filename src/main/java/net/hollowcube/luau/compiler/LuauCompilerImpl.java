package net.hollowcube.luau.compiler;

import net.hollowcube.luau.internal.compiler.lua_CompileOptions;
import net.hollowcube.luau.internal.compiler.luacode_h;
import net.hollowcube.luau.util.NativeLibraryLoader;
import org.jetbrains.annotations.NotNull;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

@SuppressWarnings("preview")
record LuauCompilerImpl(
        @NotNull OptimizationLevel optimizationLevel,
        @NotNull DebugLevel debugLevel,
        @NotNull TypeInfoLevel typeInfoLevel,
        @NotNull CoverageLevel coverageLevel
) implements LuauCompiler {
    static {
        NativeLibraryLoader.loadLibrary("compiler");
    }

    private static final MethodHandle FREE_HANDLE;

    static {
        // This is basically just a manually inlined version of what jextract would generate for `free`.

        final SymbolLookup symbolLookup = SymbolLookup.loaderLookup().or(Linker.nativeLinker().defaultLookup());
        final MemorySegment freeAddr = symbolLookup.find("free")
                .orElseThrow(() -> new UnsatisfiedLinkError("unresolved symbol: free"));
        final FunctionDescriptor freeDesc = FunctionDescriptor.ofVoid(ValueLayout.ADDRESS);

        FREE_HANDLE = Linker.nativeLinker().downcallHandle(freeAddr, freeDesc);
    }

    @Override
    public byte[] compile(byte @NotNull [] source) throws LuauCompileException {
        try (Arena arena = Arena.ofConfined()) {
            final MemorySegment sourceStr = arena.allocateArray(ValueLayout.JAVA_BYTE, source);
            final MemorySegment bytecodeSize = arena.allocate(ValueLayout.JAVA_LONG);
            final MemorySegment compileOpts = createCompileOptions(arena);

            final MemorySegment result = luacode_h.luau_compile(sourceStr, source.length, compileOpts, bytecodeSize);
            final long length = bytecodeSize.get(ValueLayout.JAVA_LONG, 0);
            final byte[] bytecode = result.asSlice(0, length).toArray(ValueLayout.JAVA_BYTE);
            free(result);

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
        return opts;
    }

    private void free(@NotNull MemorySegment segment) {
        try {
            FREE_HANDLE.invokeExact(segment);
        } catch (Throwable ex) {
            throw new AssertionError("should not reach here", ex);
        }
    }

    static final class BuilderImpl implements Builder {
        private OptimizationLevel optimizationLevel = OptimizationLevel.BASELINE;
        private DebugLevel debugLevel = DebugLevel.BACKTRACE;
        private TypeInfoLevel typeInfoLevel = TypeInfoLevel.NATIVE_MODULES;
        private CoverageLevel coverageLevel = CoverageLevel.NONE;

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
        public @NotNull LuauCompiler build() {
            return new LuauCompilerImpl(optimizationLevel, debugLevel, typeInfoLevel, coverageLevel);
        }
    }

}
