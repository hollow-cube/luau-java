package net.hollowcube.luau.compiler;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings("preview")
public sealed interface LuauCompiler permits LuauCompilerImpl {

    @NotNull LuauCompiler DEFAULT = builder().build();

    static @NotNull Builder builder() {
        return new LuauCompilerImpl.BuilderImpl();
    }

    /**
     * <p>Compiles the given source code and returns the bytecode as a byte array.</p>
     *
     * @param source The luau source code to compile.
     * @return The bytecode of the compiled source code.
     */
    byte[] compile(@NotNull String source) throws LuauCompileException;

    sealed interface Builder permits LuauCompilerImpl.BuilderImpl {

        @NotNull Builder optimizationLevel(@NotNull OptimizationLevel level);

        @NotNull Builder debugLevel(@NotNull DebugLevel level);

        @NotNull Builder typeInfoLevel(@NotNull TypeInfoLevel level);

        @NotNull Builder coverageLevel(@NotNull CoverageLevel level);

        //todo vectorLib, vectorCtor, vectorType, mutableGlobals, userdataTypes

        @NotNull LuauCompiler build();

    }
}
