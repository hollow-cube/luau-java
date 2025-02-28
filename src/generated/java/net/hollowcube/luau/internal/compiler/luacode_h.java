// Generated by jextract

package net.hollowcube.luau.internal.compiler;

import java.lang.invoke.*;
import java.lang.foreign.*;
import java.nio.ByteOrder;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import static java.lang.foreign.ValueLayout.*;
import static java.lang.foreign.MemoryLayout.PathElement.*;

public class luacode_h {

    luacode_h() {
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

    private static class luau_compile {
        public static final FunctionDescriptor DESC = FunctionDescriptor.of(
            luacode_h.C_POINTER,
            luacode_h.C_POINTER,
            luacode_h.C_LONG,
            luacode_h.C_POINTER,
            luacode_h.C_POINTER
        );

        public static final MemorySegment ADDR = luacode_h.findOrThrow("luau_compile");

        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    /**
     * Function descriptor for:
     * {@snippet lang=c :
     * extern char *luau_compile(const char *source, size_t size, lua_CompileOptions *options, size_t *outsize)
     * }
     */
    public static FunctionDescriptor luau_compile$descriptor() {
        return luau_compile.DESC;
    }

    /**
     * Downcall method handle for:
     * {@snippet lang=c :
     * extern char *luau_compile(const char *source, size_t size, lua_CompileOptions *options, size_t *outsize)
     * }
     */
    public static MethodHandle luau_compile$handle() {
        return luau_compile.HANDLE;
    }

    /**
     * Address for:
     * {@snippet lang=c :
     * extern char *luau_compile(const char *source, size_t size, lua_CompileOptions *options, size_t *outsize)
     * }
     */
    public static MemorySegment luau_compile$address() {
        return luau_compile.ADDR;
    }

    /**
     * {@snippet lang=c :
     * extern char *luau_compile(const char *source, size_t size, lua_CompileOptions *options, size_t *outsize)
     * }
     */
    public static MemorySegment luau_compile(MemorySegment source, long size, MemorySegment options, MemorySegment outsize) {
        var mh$ = luau_compile.HANDLE;
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("luau_compile", source, size, options, outsize);
            }
            return (MemorySegment)mh$.invokeExact(source, size, options, outsize);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }

    private static class luau_ext_free {
        public static final FunctionDescriptor DESC = FunctionDescriptor.ofVoid(
            luacode_h.C_POINTER
        );

        public static final MemorySegment ADDR = luacode_h.findOrThrow("luau_ext_free");

        public static final MethodHandle HANDLE = Linker.nativeLinker().downcallHandle(ADDR, DESC);
    }

    /**
     * Function descriptor for:
     * {@snippet lang=c :
     * extern void luau_ext_free(char *bytecode)
     * }
     */
    public static FunctionDescriptor luau_ext_free$descriptor() {
        return luau_ext_free.DESC;
    }

    /**
     * Downcall method handle for:
     * {@snippet lang=c :
     * extern void luau_ext_free(char *bytecode)
     * }
     */
    public static MethodHandle luau_ext_free$handle() {
        return luau_ext_free.HANDLE;
    }

    /**
     * Address for:
     * {@snippet lang=c :
     * extern void luau_ext_free(char *bytecode)
     * }
     */
    public static MemorySegment luau_ext_free$address() {
        return luau_ext_free.ADDR;
    }

    /**
     * {@snippet lang=c :
     * extern void luau_ext_free(char *bytecode)
     * }
     */
    public static void luau_ext_free(MemorySegment bytecode) {
        var mh$ = luau_ext_free.HANDLE;
        try {
            if (TRACE_DOWNCALLS) {
                traceDowncall("luau_ext_free", bytecode);
            }
            mh$.invokeExact(bytecode);
        } catch (Throwable ex$) {
           throw new AssertionError("should not reach here", ex$);
        }
    }
}

