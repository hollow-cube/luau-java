package net.hollowcube.luau;

public enum LuaCodegenResult {
    /// Native code was generated for at least one function
    SUCCESS,
    /// No function was left to compile: either they are already native, or the compiler
    /// marked them cold because it did not consider lowering them worthwhile
    NOTHING_TO_COMPILE,
    /// The module is missing a `--!native` comment (only reported when compiling
    /// native modules only, which is not how [LuaState#codegenCompile(int)] compiles)
    NOT_NATIVE_MODULE,
    /// [LuaState#codegenCreate()] was not called on this state
    NOT_INITIALIZED,
    /// Too many IR instructions
    OVERFLOW_INSTRUCTION_LIMIT,
    /// Too many IR blocks
    OVERFLOW_BLOCK_LIMIT,
    /// Too many IR instructions in a single block
    OVERFLOW_BLOCK_INSTRUCTION_LIMIT,
    /// Assembler finalization failed, usually a jump offset overflow in a very large module
    ASSEMBLER_FINALIZATION_FAILURE,
    /// An instruction could not be lowered to machine code
    LOWERING_FAILURE,
    /// Executable memory could not be allocated
    ALLOCATION_FAILED,
    /// A result Luau reported which this binding does not know about
    ///
    /// This type is documented-additive, so we need to handle unknown values.
    UNKNOWN;

    private static final LuaCodegenResult[] VALUES = values();

    public static LuaCodegenResult byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : UNKNOWN;
    }

    public int id() {
        return ordinal();
    }

    /// Whether any function was natively compiled as a result of this call.
    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
