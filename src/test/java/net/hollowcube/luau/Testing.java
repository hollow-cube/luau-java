package net.hollowcube.luau;

import net.hollowcube.luau.compiler.LuauCompiler;

import java.lang.foreign.Arena;

@SuppressWarnings("preview")
public class Testing {

    public static void main(String[] args) throws Exception {
        System.setProperty("jextract.trace.downcalls", "false");

        System.load("/Users/matt/dev/projects/hollowcube/luau-java/libLuau.VM.dylib");

        try (Arena arena = Arena.ofShared()) {
            var source = """
                    print('hello from lua')
                                        
                    -- print(add(1, 2))
                    """;
            var bytecode = LuauCompiler.DEFAULT.compile(source);

            LuaState state = Luau.newState();
            state.openLibs();

            state.load("main.lua", bytecode);
            state.pcall();

            state.close();

            // OLD OLD OLD OLD OLD


//            MemorySegment L = lualib_h.luaL_newstate();
//            lualib_h.luaL_openlibs(L);
//
//            // Load the add function from java
//
//            var addFn = lua_CFunction.allocate(L2 -> {
//                int left = lualib_h.luaL_checkinteger(L2, 1);  // Get the first argument
//                int right = lualib_h.luaL_checkinteger(L2, 2); // Get the second argument
//                lua_h.lua_pushinteger(L2, left + right);            // Push the result (arg * 2)
//                return 1;
//            }, arena);
//
//            // lua_pushcfunction(L, my_c_function);
//            var addFunctionName = arena.allocateUtf8String("add");
//            lua_h.lua_pushcclosurek(L, addFn, addFunctionName, 0, MemorySegment.NULL);
//
//            // lua_setglobal(L, "my_c_function");
//            lua_h.lua_setfield(L, lua_h.LUA_GLOBALSINDEX(), addFunctionName);
//
//            // Load the bytecode and execute it
//
//            var namecstr = arena.allocateUtf8String("main.lua");
//            var bytecodeWrapped = arena.allocateArray(ValueLayout.JAVA_BYTE, bytecode);
//            lua_h.luau_load(L, namecstr, bytecodeWrapped, bytecode.length, 0);
//
//            lua_h.lua_pcall(L, 0, 0, 0);
//
//            lua_h.lua_close(L);
        }
    }

}
