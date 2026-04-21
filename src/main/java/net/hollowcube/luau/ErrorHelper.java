package net.hollowcube.luau;

import static net.hollowcube.luau.LuaStateImpl.mergeBacktrace;

class ErrorHelper {

    static int handleError(LuaState state, Throwable t){
        return switch (t) {
            case LuaError err -> {
                // If we are OOM-ing, dont attempt to resolve a stacktrace.
                if (err.status() == LuaStatus.ERRMEM) yield (
                        -100 - LuaStatus.ERRMEM.id()
                );
                // If we have no more stack space, ignore the nice error propagation and just continue throwing.
                if (!state.checkStack(1)) yield -100 - err.status().id();

                yield err.pushAndMark(state); // Continue unwinding
            }
            default -> {
                if (t instanceof Error e) {
                    System.err.println(
                            "An unrecoverable error occurred within a LuaFunc, the VM will crash."
                    );
                    throw e;
                }

                // We got an error in java, merge with the lua stacktrace then put it on the stack and continue.
                String message = t.getClass().getName();
                if (t.getMessage() != null) message += ": " + t.getMessage();

                final LuaError err = new LuaError(message);
                err.setStackTrace(mergeBacktrace(state, t.getStackTrace(), false));
                yield err.pushAndMark(state);
            }
        };
    }
}
