package net.hollowcube.luau;

import net.hollowcube.luau.compiler.LuauCompiler;

@SuppressWarnings("preview")
public class Testing {

    public static void main(String[] args) throws Exception {
        System.setProperty("jextract.trace.downcalls", "false");

        System.load("/Users/matt/dev/projects/hollowcube/luau-java/libLuau.VM.dylib");

        var source = """
                print('hello from lua')
                                
                print(add(1, 2))
                print(sub(1, 2))
                """;
        var bytecode = LuauCompiler.DEFAULT.compile(source);

        try (LuaState global = Luau.newState()) {
            global.openLibs();

            global.defineGlobalFunction("add", state -> {
                int left = state.checkInt(1);
                int right = state.checkInt(2);
                state.pushInt(left + right);
                return 1;
            });

            global.defineGlobalFunction("sub", state -> {
                int left = state.checkInt(1);
                int right = state.checkInt(2);
                state.pushInt(left - right);
                return 1;
            });

            // After this point it is invalid to do any setglobal calls.
            // We should check this in java land because it segfaults.
            global.sandbox();

            try (LuaState thread = global.newThread()) {

//                MemorySegment interruptFunc = lua_Callbacks.interrupt.allocate((L, gc) -> {
//                    System.out.println("interrupt called!");
//                }, Arena.global());

//                MemorySegment panicFunc = lua_Callbacks.panic.allocate((L, errcode) -> {
//                    System.out.println("PANIC: " + errcode);
//                }, Arena.global());

//                MemorySegment callbacks = lua_h.lua_callbacks(thread.L());
//                lua_Callbacks.interrupt(callbacks, interruptFunc);
//                lua_Callbacks.panic(callbacks, panicFunc);

                thread.sandboxThread();

                // Now ready for running untrusted code.

                thread.load("main.lua", bytecode);
                thread.pcall();
            }

            global.pop(1); // the thread was added to the stack, remove it.

        }
    }

}
