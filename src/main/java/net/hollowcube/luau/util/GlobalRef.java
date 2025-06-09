package net.hollowcube.luau.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class GlobalRef {

    static {
        NativeLibraryLoader.loadLibrary("globalref");
    }

    public static native long newref(Object obj);

    public static native void unref(long ref);

    public static native @NotNull Object get(long ref);

    public static native long newweakref(Object obj);

    public static native @Nullable Object getweakref(long ref);

}
