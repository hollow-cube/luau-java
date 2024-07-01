package net.hollowcube.luau;

public enum LuaGCOp {
    STOP,
    RESTART,

    COLLECT,

    COUNT,
    COUNTB,

    ISRUNNING,

    STEP,

    SETGOAL,
    STEPMUL,
    STEPSIZE;

    public int id() {
        return ordinal();
    }
}
