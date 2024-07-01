package net.hollowcube.luau;

import net.hollowcube.luau.compiler.LuauCompiler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings("preview")
public class Testing {

    //todo NEED TO COME UP WITH SOME WAY TO MAKE SURE A PROGRAM DOESNT LEAK REFERENCES.
    // some env var which adds them all to a list to check would probably work.

    public static void main(String[] args) throws Exception {
        System.load("/Users/matt/dev/projects/hollowcube/luau-java/libLuau.VM.dylib");

        var source = """
                print('hello from lua')
                                
                local arr = m2.newarray()
                m2.push(arr, 1)
                m2.push(arr, 2)
                m2.show(arr)

                print(m2.add(1, 2))
                print(m2.sub(1, 2))
                abc()
                """;
        var bytecode = LuauCompiler.DEFAULT.compile(source);

        LuaState global = Luau.newState();
        try {
            global.openLibs();

            global.newMetaTable("myarray");
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
                    "push", state -> {
                        List<Integer> arr = (List<Integer>) state.checkUserDataArg(1, "myarray");
                        int value = state.checkIntegerArg(2);
                        arr.add(value);
                        return 0;
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
            thread.load("main.lua", bytecode);
            thread.pcall(0, 0);

            global.pop(1); // the thread was added to the stack, remove it so that it can be garbage collected.

        } finally {
            global.close();
        }
    }

}
