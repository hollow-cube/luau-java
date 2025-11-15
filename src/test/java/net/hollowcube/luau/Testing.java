package net.hollowcube.luau;

import net.hollowcube.luau.compiler.LuauCompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Testing {

    //todo NEED TO COME UP WITH SOME WAY TO MAKE SURE A PROGRAM DOESNT LEAK REFERENCES.
    // some env var which adds them all to a list to check would probably work.

    static void main(String[] args) throws Exception {
        var source = """
                a(function()
                    b(function()
                        print('hello from lua')
                        error('hello')
                    end)
                end)
                """;
        //        var source = """
        //                print('hello from lua')
        //
        //                local arr = m2.newarray()
        //                arr:push(1)
        //                arr:push(2)
        //                m2.show(arr)
        //
        //                print(m2.add(1, 2))
        //                print(m2.sub(1, 2))
        //                abc()
        //                """;
        var bytecode = LuauCompiler.DEFAULT.compile(source);

        LuaState global = LuaState.newState();
        try {
            global.openLibs();

            global.pushCFunction(state -> {
                // Call function at first index
                state.pushValue(1);
                state.call(0, 0);
                return 0;
            }, "a");
            global.setGlobal("a");
            global.pushCFunction(state -> {
                // Call function at first index
                try {
                    if (Math.random() > 0) {
                        throw new RuntimeException("random error");
                    }
                    state.pushValue(1);
                    state.call(0, 0);
                } finally {
                    System.out.println("b finally");
                }
                System.out.println("b is ending");
                return 0;
            }, "b");
            global.setGlobal("b");

            global.newMetaTable("myarray");
            global.pushString("__index");
            global.pushValue(-2);
            global.setTable(-3);
            global.registerLib(null, Map.of(
                    "push", state -> {
                        List<Integer> arr = (List<Integer>) state.checkUserDataArg(1, "myarray");
                        int value = state.checkIntegerArg(2);
                        arr.add(value);
                        return 0;
                    }
            ));

            global.registerLib("m2", Map.of(
                    "add", state -> {
                        int left = state.checkIntegerArg(1);
                        int right = state.checkIntegerArg(2);
                        state.pushInteger(left + right);
                        return 1;
                    },
                    "sub", state -> {
                        int left = state.checkIntegerArg(1);
                        int right = state.checkIntegerArg(2);
                        state.pushInteger(left - right);
                        //                        state.error("error from java");
                        return 1;
                    },
                    "newarray", state -> {

                        List<Integer> arr = new ArrayList<>();
                        state.newUserData(arr);

                        // Assign the metatable. todo: the library should just handle this IMO
                        state.getMetaTable("myarray");
                        state.setMetaTable(-2);

                        return 1;
                    },
                    "show", state -> {
                        List<Integer> arr = (List<Integer>) state.checkUserDataArg(1, "myarray");
                        System.out.println("array: " + arr);
                        return 0;
                    }
            ));
            global.pushCFunction(_ -> {
                System.out.println("hello from java");
                return 0;
            }, "abc");
            global.setGlobal("abc");

            // After this point it is invalid to do any setglobal calls.
            // We should check this in java land because it segfaults.
            global.sandbox();

            LuaState thread = global.newThread();
            thread.sandboxThread();
            // Now ready for running untrusted code.

            for (int i = 0; i < 200; i++) {
                try {
                    thread.load("main.lua", bytecode);
                    thread.pcall(0, 0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            global.pop(1); // the thread was added to the stack, remove it so that it can be garbage collected.

        } finally {
            global.close();
        }
    }

}
