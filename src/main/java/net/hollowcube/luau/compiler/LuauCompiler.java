package net.hollowcube.luau.compiler;

import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.List;

@SuppressWarnings("preview")
public sealed interface LuauCompiler permits LuauCompilerImpl {

    @NotNull
    LuauCompiler DEFAULT = builder().build();

    static @NotNull Builder builder() {
        return new LuauCompilerImpl.BuilderImpl();
    }

    /**
     * <p>Compiles the given source code and returns the bytecode as a byte array.</p>
     *
     * @param source The luau source code to compile.
     * @return The bytecode of the compiled source code.
     */
    default byte[] compile(@NotNull String source) throws LuauCompileException {
        return compile(source.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * <p>Compiles the given source code and returns the bytecode as a byte array.</p>
     *
     * @param source The luau source code to compile.
     * @return The bytecode of the compiled source code.
     */
    byte[] compile(byte @NotNull [] source) throws LuauCompileException;

    sealed interface Builder permits LuauCompilerImpl.BuilderImpl {

        @NotNull Builder optimizationLevel(@NotNull OptimizationLevel level);

        @NotNull Builder debugLevel(@NotNull DebugLevel level);

        @NotNull Builder typeInfoLevel(@NotNull TypeInfoLevel level);

        @NotNull Builder coverageLevel(@NotNull CoverageLevel level);

        /**
         * name of the library providing vector type operations
         */
        @NotNull Builder vectorLib(@NotNull String vectorLib);

        /**
         * global builtin to construct vectors; disabled by default
         */
        @NotNull Builder vectorCtor(@NotNull String vectorCtor);

        /**
         * vector type name for type tables; disabled by default
         */
        @NotNull Builder vectorType(@NotNull String vectorType);

        /**
         * List of globals that are mutable; disables the import optimization for fields accessed through these
         * <br/>
         * Replaces any previously set value.
         */
        default @NotNull Builder mutableGlobals(@NotNull String... mutableGlobals) {
            return mutableGlobals(List.of(mutableGlobals));
        }

        /**
         * List of globals that are mutable; disables the import optimization for fields accessed through these
         * <br/>
         * Replaces any previously set value.
         */
        @NotNull Builder mutableGlobals(@NotNull List<String> mutableGlobals);

        /**
         * userdata types that will be included in the type information
         * <br/>
         * Replaces any previously set value.
         */
        default @NotNull Builder userdataTypes(@NotNull String... userdataTypes) {
            return userdataTypes(List.of(userdataTypes));
        }

        /**
         * userdata types that will be included in the type information
         * <br/>
         * Replaces any previously set value.
         */
        @NotNull Builder userdataTypes(@NotNull List<String> userdataTypes);

        @NotNull LuauCompiler build();

    }
}
