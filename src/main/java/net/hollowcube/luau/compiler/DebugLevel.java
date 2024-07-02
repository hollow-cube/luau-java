package net.hollowcube.luau.compiler;

/**
 * <p>Defaults to {@link #BACKTRACE}.</p>
 */
public enum DebugLevel {
    /**
     * no debugging support
     */
    NONE,
    /**
     * line info and function names only; sufficient for backtraces
     */
    BACKTRACE,
    /**
     * full debug info with local and upvalue names; necessary for debugger
     */
    DEBUGGER,
}
