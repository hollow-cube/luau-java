package net.hollowcube.luau;

public enum LuaGcOp {
    /// stop and resume incremental garbage collection
    STOP,
    RESTART,

    /// run a full GC cycle; not recommended for latency sensitive applications
    COLLECT,

    /// return the heap size in KB and the remainder in bytes
    COUNT,
    COUNTB,

    /// return 1 if GC is active (not stopped); note that GC may not be actively collecting even if it's running
    IS_RUNNING,

    /// perform an explicit GC step, with the step size specified in KB
    ///
    /// garbage collection is handled by 'assists' that perform some amount of GC work matching pace of allocation
    /// explicit GC steps allow to perform some amount of work at custom points to offset the need for GC assists
    /// note that GC might also be paused for some duration (until bytes allocated meet the threshold)
    /// if an explicit step is performed during this pause, it will trigger the start of the next collection cycle
    STEP,

    /// tune GC parameters G (goal), S (step multiplier) and step size (usually best left ignored)
    ///
    /// garbage collection is incremental and tries to maintain the heap size to balance memory and performance overhead
    /// this overhead is determined by G (goal) which is the ratio between total heap size and the amount of live data in it
    /// G is specified in percentages; by default G=200% which means that the heap is allowed to grow to ~2x the size of live data.
    ///
    /// collector tries to collect S% of allocated bytes by interrupting the application after step size bytes were allocated.
    /// when S is too small, collector may not be able to catch up and the effective goal that can be reached will be larger.
    /// S is specified in percentages; by default S=200% which means that collector will run at ~2x the pace of allocations.
    ///
    /// it is recommended to set S in the interval [100 /(G - 100), 100 + 100 / (G - 100))] with a minimum value of 150%; for example:
    /// - for G=200%, S should be in the interval [150%, 200%]
    /// - for G=150%, S should be in the interval [200%, 300%]
    /// - for G=125%, S should be in the interval [400%, 500%]
    SET_GOAL,
    SET_STEP_MUL,
    SET_STEP_SIZE,
}
