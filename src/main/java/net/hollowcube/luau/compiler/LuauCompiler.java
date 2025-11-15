package net.hollowcube.luau.compiler;

import java.nio.charset.StandardCharsets;
import java.util.List;

// TODO: should move compiler to its own module so its possible to make a lighter dependency for those use cases.
public sealed interface LuauCompiler permits LuauCompilerImpl {

    LuauCompiler DEFAULT = builder().build();

    static Builder builder() {
        return new LuauCompilerImpl.BuilderImpl();
    }

    /**
     * <p>Compiles the given source code and returns the bytecode as a byte array.</p>
     *
     * @param source The luau source code to compile.
     * @return The bytecode of the compiled source code.
     */
    default byte[] compile(String source) throws LuauCompileException {
        return compile(source.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * <p>Compiles the given source code and returns the bytecode as a byte array.</p>
     *
     * @param source The luau source code to compile.
     * @return The bytecode of the compiled source code.
     */
    byte[] compile(byte[] source) throws LuauCompileException;

    sealed interface Builder permits LuauCompilerImpl.BuilderImpl {

        Builder optimizationLevel(OptimizationLevel level);

        Builder debugLevel(DebugLevel level);

        Builder typeInfoLevel(TypeInfoLevel level);

        Builder coverageLevel(CoverageLevel level);

        /**
         * name of the library providing vector type operations
         */
        Builder vectorLib(String vectorLib);

        /**
         * global builtin to construct vectors; disabled by default
         */
        Builder vectorCtor(String vectorCtor);

        /**
         * vector type name for type tables; disabled by default
         */
        Builder vectorType(String vectorType);

        /**
         * List of globals that are mutable; disables the import optimization for fields accessed through these
         * <br/>
         * Replaces any previously set value.
         */
        default Builder mutableGlobals(String... mutableGlobals) {
            return mutableGlobals(List.of(mutableGlobals));
        }

        /**
         * List of globals that are mutable; disables the import optimization for fields accessed through these
         * <br/>
         * Replaces any previously set value.
         */
        Builder mutableGlobals(List<String> mutableGlobals);

        /**
         * userdata types that will be included in the type information
         * <br/>
         * Replaces any previously set value.
         */
        default Builder userdataTypes(String... userdataTypes) {
            return userdataTypes(List.of(userdataTypes));
        }

        /**
         * userdata types that will be included in the type information
         * <br/>
         * Replaces any previously set value.
         */
        Builder userdataTypes(List<String> userdataTypes);

        LuauCompiler build();

    }
}
