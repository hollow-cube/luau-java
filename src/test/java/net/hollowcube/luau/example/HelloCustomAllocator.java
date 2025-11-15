package net.hollowcube.luau.example;

import net.hollowcube.luau.compiler.LuauCompileException;

public class HelloCustomAllocator {

    public static void main(String[] args) throws LuauCompileException {
        // TODO: re-add
        //        final byte[] bytecode = LuauCompiler.DEFAULT.compile("""
        //                        print("Hello, Luau!")
        //                        print(testObject:ComputeThing())
        //                """);
        //
        //        final var reallocPtr = Linker.nativeLinker().defaultLookup().find("realloc").get();
        //        final var freePtr = Linker.nativeLinker().defaultLookup().find("free").get();
        //
        //        final var free = Linker.nativeLinker().downcallHandle(freePtr, FunctionDescriptor.ofVoid(lua_h.C_POINTER));
        //        final var realloc = Linker.nativeLinker().downcallHandle(reallocPtr, FunctionDescriptor.of(lua_h.C_POINTER, lua_h.C_POINTER, lua_h.C_LONG));
        //
        //        final LuaState state = LuaState.newState((ud, ptr, osize, nsize) -> {
        //
        //            try {
        //                if (nsize == 0) {
        //                    free.invokeExact(ptr);
        //                    return NULL;
        //                } else {
        //                    System.out.println("Allocating " + nsize);
        //                    return (MemorySegment) realloc.invokeExact(ptr, nsize);
        //                    // Force Luau to throw out-of-memory error by returning null here:
        //                    // return MemorySegment.NULL;
        //                }
        //
        //            } catch (Throwable t) {
        //                t.printStackTrace();
        //                return NULL;
        //            }
        //
        //        });
        //
        //        try {
        //            state.openLibs(); // Open all libraries
        //            TestClass.init(state);
        //            state.sandbox(); // Sandbox the global state so it cannot be edited by a script
        //
        //            var thread = state.newThread();
        //            thread.sandboxThread(); // Create a mutable global env for scripts to use
        //
        //            thread.load("helloworld.luau", bytecode); // Load the script into the VM
        //            thread.pcall(0, 0); // Eval the script
        //
        //            state.pop(1); // Pop the thread off the stack
        //        } finally {
        //            // Always remember to close the state when you're done with it, or you will leak memory.
        //            state.close();
        //        }
    }
}
