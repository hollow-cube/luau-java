package net.hollowcube.luau.util;

public class JNIRefTest {

    static {
        System.loadLibrary("jnitest");
    }

    public static native long newref(Object obj);

    public static native void unref(long ref);

    public static native Object get(long ref);

}
