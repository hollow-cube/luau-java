package net.hollowcube.luau;

import net.hollowcube.luau.compiler.LuauCompiler;
import net.hollowcube.luau.internal.compiler.lua_CompileOptions;
import net.hollowcube.luau.internal.compiler.luacode_h;
import net.hollowcube.luau.internal.vm.*;

import java.lang.foreign.Arena;
import java.util.*;

public class ForceLoadAll {
    static void main() {
        var _ = LuauCompiler.DEFAULT;
        var _ = LuaState.LIGHTUSERDATA_TAG_LIMIT;

        var classes = Set.of(
                lua_CompileOptions.class,
                luacode_h.class,
                lua_Alloc.class,
                lua_Callbacks.class,
                lua_CFunction.class,
                lua_h.class,
                lua_newuserdatadtor$dtor.class,
                luaL_Reg.class,
                lualib_h.class
        );
        var upcalls = new HashMap<Class<?>, Object>();
        upcalls.put(lua_CFunction.class, (lua_CFunction.Function) (_) -> unreachable());
        upcalls.put(lua_Alloc.class, (lua_Alloc.Function) (_, _, _, _) -> unreachable());
        upcalls.put(lua_newuserdatadtor$dtor.class, (lua_newuserdatadtor$dtor.Function) (_) -> unreachable());
        upcalls.put(lua_Callbacks.onallocate.class, (lua_Callbacks.onallocate.Function) (_, _, _) -> unreachable());
        upcalls.put(lua_Callbacks.debugprotectederror.class,
                    (lua_Callbacks.debugprotectederror.Function) (_) -> unreachable());
        upcalls.put(lua_Callbacks.debuginterrupt.class,
                    (lua_Callbacks.debuginterrupt.Function) (_, _) -> unreachable());
        upcalls.put(lua_Callbacks.debugstep.class, (lua_Callbacks.debugstep.Function) (_, _) -> unreachable());
        upcalls.put(lua_Callbacks.debugbreak.class, (lua_Callbacks.debugbreak.Function) (_, _) -> unreachable());
        upcalls.put(lua_Callbacks.useratom.class, (lua_Callbacks.useratom.Function) (_, _) -> unreachable());
        upcalls.put(lua_Callbacks.userthread.class, (lua_Callbacks.userthread.Function) (_, _) -> unreachable());
        upcalls.put(lua_Callbacks.panic.class, (lua_Callbacks.panic.Function) (_, _) -> unreachable());
        upcalls.put(lua_Callbacks.interrupt.class, (lua_Callbacks.interrupt.Function) (_, _) -> unreachable());

        var toLoad = new ArrayDeque<>(classes);
        while (!toLoad.isEmpty()) {
            var classToLoad = toLoad.pop();
            try {
                System.out.println("load: " + classToLoad.getName());
                var theClass = Class.forName(classToLoad.getName());
                toLoad.addAll(List.of(theClass.getDeclaredClasses()));

                for (var field : theClass.getDeclaredFields()) {
                    if (!field.getName().equals("UP$MH")) continue;

                    var functionClass = theClass.getDeclaredClasses()[0];
                    var instance = Objects.requireNonNull(upcalls.get(theClass),
                                                          "no upcall for " + theClass.getName());
                    try (var arena = Arena.ofConfined()) {
                        theClass.getDeclaredMethod("allocate", functionClass, Arena.class)
                                .invoke(null, instance, arena);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static <T> T unreachable() {
        throw new RuntimeException("unreachable");
    }
}
