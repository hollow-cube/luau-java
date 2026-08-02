package net.hollowcube.luau.util;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class GlobalRef {

    static {
        NativeLibraryLoader.loadLibrary("luaujava");
    }

    public static native long newref(Object obj);

    public static native void unref(long ref);

    public static native Object get(long ref);
}
