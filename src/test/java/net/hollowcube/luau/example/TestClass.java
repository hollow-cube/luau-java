package net.hollowcube.luau.example;

import net.hollowcube.luau.LuaFunc;
import net.hollowcube.luau.LuaState;
import org.jetbrains.annotations.NotNull;

public class TestClass {

    public static void init(@NotNull LuaState global) {
        // Create ComputeThing lua function handle.
        LuaFunc computeThingHandle = LuaFunc.preallocate(state -> {
            var instance = (TestClass) state.checkUserDataArg(1, "TestClass");
            state.pushString(instance.ComputeThing());
            return 1;
        });

        global.newUserData(new TestClass());
        global.newMetaTable("TestClass");
        global.pushCFunction(state -> {
            var target = state.checkStringArg(2);
            if ("ComputeThing".equals(target)) {
                state.pushCFunction(computeThingHandle, "ComputeThing");
            } else {
                state.pushNil();
            }
            return 1;
        }, "__index");
        global.setField(-2, "__index");
        // metatable still on top of stack

        global.setMetaTable(-2);
        global.setGlobal("testObject");
    }

    private String ComputeThing() {
        return "Hello From Java";
    }
}
