// Generated by jextract

package net.hollowcube.luau.internal.vm;

import java.lang.invoke.*;
import java.lang.foreign.*;
import java.nio.ByteOrder;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import static java.lang.foreign.ValueLayout.*;
import static java.lang.foreign.MemoryLayout.PathElement.*;

public class lua_h {

    lua_h() {
        // Should not be called directly
    }

    static final Arena LIBRARY_ARENA = Arena.ofAuto();
    static final boolean TRACE_DOWNCALLS = Boolean.getBoolean("jextract.trace.downcalls");

    static void traceDowncall(String name, Object... args) {
         String traceArgs = Arrays.stream(args)
                       .map(Object::toString)
                       .collect(Collectors.joining(", "));
         System.out.printf("%s(%s)\n", name, traceArgs);
    }

    static MemorySegment findOrThrow(String symbol) {
        return SYMBOL_LOOKUP.find(symbol)
            .orElseThrow(() -> new UnsatisfiedLinkError("unresolved symbol: " + symbol));
    }

    static MethodHandle upcallHandle(Class<?> fi, String name, FunctionDescriptor fdesc) {
        try {
            return MethodHandles.lookup().findVirtual(fi, name, fdesc.toMethodType());
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    static MemoryLayout align(MemoryLayout layout, long align) {
        return switch (layout) {
            case PaddingLayout p -> p;
            case ValueLayout v -> v.withByteAlignment(align);
            case GroupLayout g -> {
                MemoryLayout[] alignedMembers = g.memberLayouts().stream()
                        .map(m -> align(m, align)).toArray(MemoryLayout[]::new);
                yield g instanceof StructLayout ?
                        MemoryLayout.structLayout(alignedMembers) : MemoryLayout.unionLayout(alignedMembers);
            }
            case SequenceLayout s -> MemoryLayout.sequenceLayout(s.elementCount(), align(s.elementLayout(), align));
        };
    }

    static final SymbolLookup SYMBOL_LOOKUP = SymbolLookup.loaderLookup()
            .or(Linker.nativeLinker().defaultLookup());

    public static final ValueLayout.OfBoolean C_BOOL = ValueLayout.JAVA_BOOLEAN;
    public static final ValueLayout.OfByte C_CHAR = ValueLayout.JAVA_BYTE;
    public static final ValueLayout.OfShort C_SHORT = ValueLayout.JAVA_SHORT;
    public static final ValueLayout.OfInt C_INT = ValueLayout.JAVA_INT;
    public static final ValueLayout.OfLong C_LONG_LONG = ValueLayout.JAVA_LONG;
    public static final ValueLayout.OfFloat C_FLOAT = ValueLayout.JAVA_FLOAT;
    public static final ValueLayout.OfDouble C_DOUBLE = ValueLayout.JAVA_DOUBLE;
    public static final AddressLayout C_POINTER = ValueLayout.ADDRESS
            .withTargetLayout(MemoryLayout.sequenceLayout(java.lang.Long.MAX_VALUE, JAVA_BYTE));
    public static final ValueLayout.OfLong C_LONG = ValueLayout.JAVA_LONG;

    private static class lua_close {
        public static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(
            lua_h.C_POINTER
        );

        public static final MemorySegment ADDR = lua_h.findOrThrow("lua_close");

        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    /**
     * Function descriptor for:
     * {@snippet lang=c :
     * extern void lua_close(lua_State *L)
     * }
     */
    public static FunctionDescriptor lua_close$descriptor() {
        return lua_close.DESC;
    }

    /**
     * Downcall method handle for:
     * {@snippet lang=c :
     * extern void lua_close(lua_State *L)
     * }
     */
    public static MethodHandle lua_close$handle() {
        return lua_close.HANDLE;
    }

    /**
     * Address for:
     * {@snippet lang=c :
     * extern void lua_close(lua_State *L)
     * }
     */
    public static MemorySegment lua_close$address() {
        return lua_close.ADDR;
    }

    /**
     * {@snippet lang=c :
     * extern void lua_close(lua_State *L)
     * }
     */
    public static void lua_close(MemorySegment L) {
        var mh$ = lua_close.HANDLE;
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("lua_close", L);
            }
            mh$.invokeExact(L);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class lua_pushinteger {
        public static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(
            lua_h.C_POINTER,
            lua_h.C_INT
        );

        public static final MemorySegment ADDR = lua_h.findOrThrow("lua_pushinteger");

        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    /**
     * Function descriptor for:
     * {@snippet lang=c :
     * extern void lua_pushinteger(lua_State *L, int n)
     * }
     */
    public static FunctionDescriptor lua_pushinteger$descriptor() {
        return lua_pushinteger.DESC;
    }

    /**
     * Downcall method handle for:
     * {@snippet lang=c :
     * extern void lua_pushinteger(lua_State *L, int n)
     * }
     */
    public static MethodHandle lua_pushinteger$handle() {
        return lua_pushinteger.HANDLE;
    }

    /**
     * Address for:
     * {@snippet lang=c :
     * extern void lua_pushinteger(lua_State *L, int n)
     * }
     */
    public static MemorySegment lua_pushinteger$address() {
        return lua_pushinteger.ADDR;
    }

    /**
     * {@snippet lang=c :
     * extern void lua_pushinteger(lua_State *L, int n)
     * }
     */
    public static void lua_pushinteger(MemorySegment L, int n) {
        var mh$ = lua_pushinteger.HANDLE;
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("lua_pushinteger", L, n);
            }
            mh$.invokeExact(L, n);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class lua_pushcclosurek {
        public static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(
            lua_h.C_POINTER,
            lua_h.C_POINTER,
            lua_h.C_POINTER,
            lua_h.C_INT,
            lua_h.C_POINTER
        );

        public static final MemorySegment ADDR = lua_h.findOrThrow("lua_pushcclosurek");

        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    /**
     * Function descriptor for:
     * {@snippet lang=c :
     * extern void lua_pushcclosurek(lua_State *L, lua_CFunction fn, const char *debugname, int nup, lua_Continuation cont)
     * }
     */
    public static FunctionDescriptor lua_pushcclosurek$descriptor() {
        return lua_pushcclosurek.DESC;
    }

    /**
     * Downcall method handle for:
     * {@snippet lang=c :
     * extern void lua_pushcclosurek(lua_State *L, lua_CFunction fn, const char *debugname, int nup, lua_Continuation cont)
     * }
     */
    public static MethodHandle lua_pushcclosurek$handle() {
        return lua_pushcclosurek.HANDLE;
    }

    /**
     * Address for:
     * {@snippet lang=c :
     * extern void lua_pushcclosurek(lua_State *L, lua_CFunction fn, const char *debugname, int nup, lua_Continuation cont)
     * }
     */
    public static MemorySegment lua_pushcclosurek$address() {
        return lua_pushcclosurek.ADDR;
    }

    /**
     * {@snippet lang=c :
     * extern void lua_pushcclosurek(lua_State *L, lua_CFunction fn, const char *debugname, int nup, lua_Continuation cont)
     * }
     */
    public static void lua_pushcclosurek(MemorySegment L, MemorySegment fn, MemorySegment debugname, int nup, MemorySegment cont) {
        var mh$ = lua_pushcclosurek.HANDLE;
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("lua_pushcclosurek", L, fn, debugname, nup, cont);
            }
            mh$.invokeExact(L, fn, debugname, nup, cont);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class lua_setfield {
        public static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(
            lua_h.C_POINTER,
            lua_h.C_INT,
            lua_h.C_POINTER
        );

        public static final MemorySegment ADDR = lua_h.findOrThrow("lua_setfield");

        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    /**
     * Function descriptor for:
     * {@snippet lang=c :
     * extern void lua_setfield(lua_State *L, int idx, const char *k)
     * }
     */
    public static FunctionDescriptor lua_setfield$descriptor() {
        return lua_setfield.DESC;
    }

    /**
     * Downcall method handle for:
     * {@snippet lang=c :
     * extern void lua_setfield(lua_State *L, int idx, const char *k)
     * }
     */
    public static MethodHandle lua_setfield$handle() {
        return lua_setfield.HANDLE;
    }

    /**
     * Address for:
     * {@snippet lang=c :
     * extern void lua_setfield(lua_State *L, int idx, const char *k)
     * }
     */
    public static MemorySegment lua_setfield$address() {
        return lua_setfield.ADDR;
    }

    /**
     * {@snippet lang=c :
     * extern void lua_setfield(lua_State *L, int idx, const char *k)
     * }
     */
    public static void lua_setfield(MemorySegment L, int idx, MemorySegment k) {
        var mh$ = lua_setfield.HANDLE;
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("lua_setfield", L, idx, k);
            }
            mh$.invokeExact(L, idx, k);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class luau_load {
        public static final FunctionDescriptor DESC = FunctionDescriptor.of(
            lua_h.C_INT,
            lua_h.C_POINTER,
            lua_h.C_POINTER,
            lua_h.C_POINTER,
            lua_h.C_LONG,
            lua_h.C_INT
        );

        public static final MemorySegment ADDR = lua_h.findOrThrow("luau_load");

        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    /**
     * Function descriptor for:
     * {@snippet lang=c :
     * extern int luau_load(lua_State *L, const char *chunkname, const char *data, size_t size, int env)
     * }
     */
    public static FunctionDescriptor luau_load$descriptor() {
        return luau_load.DESC;
    }

    /**
     * Downcall method handle for:
     * {@snippet lang=c :
     * extern int luau_load(lua_State *L, const char *chunkname, const char *data, size_t size, int env)
     * }
     */
    public static MethodHandle luau_load$handle() {
        return luau_load.HANDLE;
    }

    /**
     * Address for:
     * {@snippet lang=c :
     * extern int luau_load(lua_State *L, const char *chunkname, const char *data, size_t size, int env)
     * }
     */
    public static MemorySegment luau_load$address() {
        return luau_load.ADDR;
    }

    /**
     * {@snippet lang=c :
     * extern int luau_load(lua_State *L, const char *chunkname, const char *data, size_t size, int env)
     * }
     */
    public static int luau_load(MemorySegment L, MemorySegment chunkname, MemorySegment data, long size, int env) {
        var mh$ = luau_load.HANDLE;
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("luau_load", L, chunkname, data, size, env);
            }
            return (int)mh$.invokeExact(L, chunkname, data, size, env);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class lua_pcall {
        public static final FunctionDescriptor DESC = FunctionDescriptor.of(
            lua_h.C_INT,
            lua_h.C_POINTER,
            lua_h.C_INT,
            lua_h.C_INT,
            lua_h.C_INT
        );

        public static final MemorySegment ADDR = lua_h.findOrThrow("lua_pcall");

        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    /**
     * Function descriptor for:
     * {@snippet lang=c :
     * extern int lua_pcall(lua_State *L, int nargs, int nresults, int errfunc)
     * }
     */
    public static FunctionDescriptor lua_pcall$descriptor() {
        return lua_pcall.DESC;
    }

    /**
     * Downcall method handle for:
     * {@snippet lang=c :
     * extern int lua_pcall(lua_State *L, int nargs, int nresults, int errfunc)
     * }
     */
    public static MethodHandle lua_pcall$handle() {
        return lua_pcall.HANDLE;
    }

    /**
     * Address for:
     * {@snippet lang=c :
     * extern int lua_pcall(lua_State *L, int nargs, int nresults, int errfunc)
     * }
     */
    public static MemorySegment lua_pcall$address() {
        return lua_pcall.ADDR;
    }

    /**
     * {@snippet lang=c :
     * extern int lua_pcall(lua_State *L, int nargs, int nresults, int errfunc)
     * }
     */
    public static int lua_pcall(MemorySegment L, int nargs, int nresults, int errfunc) {
        var mh$ = lua_pcall.HANDLE;
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("lua_pcall", L, nargs, nresults, errfunc);
            }
            return (int)mh$.invokeExact(L, nargs, nresults, errfunc);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }
    private static final int LUA_GLOBALSINDEX = (int)-10002L;
    /**
     * {@snippet lang=c :
     * #define LUA_GLOBALSINDEX -10002
     * }
     */
    public static int LUA_GLOBALSINDEX() {
        return LUA_GLOBALSINDEX;
    }
}

