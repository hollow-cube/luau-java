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
     * line info & function names only; sufficient for backtraces
     */
    BACKTRACE,
    /**
     * full debug info with local & upvalue names; necessary for debugger
     */
    DEBUGGER,
}
