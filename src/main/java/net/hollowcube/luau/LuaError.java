package net.hollowcube.luau;

import org.jetbrains.annotations.CheckReturnValue;
import org.jetbrains.annotations.Nullable;

public class LuaError extends RuntimeException {

    private final LuaStatus status;

    LuaError(@Nullable String message) {
        this(LuaStatus.ERRRUN, message);
    }

    LuaError(LuaStatus status, @Nullable String message) {
        super(message);
        this.status = status;
    }

    public LuaStatus status() {
        return status;
    }

    /// Push the error onto the stack so its discoverable by intermediate handlers, and
    /// create the error marker return value for luau to continue unwinding the callstack.
    @CheckReturnValue
    int pushAndMark(LuaState state) {
        state.newUserData(this);
        return -100 - status().id();
    }
}
