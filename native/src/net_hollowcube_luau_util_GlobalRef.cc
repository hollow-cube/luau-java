#include "net_hollowcube_luau_util_GlobalRef.h"

jlong JNICALL Java_net_hollowcube_luau_util_GlobalRef_newref(JNIEnv *env, jclass clazz, jobject target) {
    return (jlong) env->NewGlobalRef(target);
}

void JNICALL Java_net_hollowcube_luau_util_GlobalRef_unref(JNIEnv *env, jclass clazz, jlong ref) {
    env->DeleteGlobalRef((jobject) ref);
}

jobject JNICALL Java_net_hollowcube_luau_util_GlobalRef_get(JNIEnv *env, jclass clazz, jlong ref) {
    return (jobject) ref;
}
