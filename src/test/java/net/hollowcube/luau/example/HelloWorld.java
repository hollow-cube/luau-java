package net.hollowcube.luau.example;

import net.hollowcube.luau.LuaState;
import net.hollowcube.luau.compiler.LuauCompileException;
import net.hollowcube.luau.compiler.LuauCompiler;

public class HelloWorld {

    public static void main(String[] args) throws LuauCompileException {
        final byte[] bytecode = LuauCompiler.DEFAULT.compile("""
                        print("Hello, Luau!")
                """);

        final LuaState state = LuaState.newState();
        try {
            state.openLibs(); // Open all libraries
            state.sandbox(); // Sandbox the global state so it cannot be edited by a script

            state.load("helloworld.luau", bytecode); // Load the script into the VM
            state.pcall(0, 0); // Eval the script
        } finally {
            // Always remember to close the state when you're done with it, or you will leak memory.
            state.close();
        }
    }

}
