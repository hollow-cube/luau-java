#include "net_hollowcube_luau_util_JNIRefTest.h"

jlong JNICALL Java_net_hollowcube_luau_util_JNIRefTest_newref(JNIEnv *env, jclass clazz, jobject target) {
    return (jlong) (*env)->NewGlobalRef(env, target);
}

void JNICALL Java_net_hollowcube_luau_util_JNIRefTest_unref(JNIEnv *env, jclass clazz, jlong ref) {
    (*env)->DeleteGlobalRef(env, (jobject) ref);
}

jobject JNICALL Java_net_hollowcube_luau_util_JNIRefTest_get(JNIEnv *env, jclass clazz, jlong ref) {
    return (jobject) ref;
}
