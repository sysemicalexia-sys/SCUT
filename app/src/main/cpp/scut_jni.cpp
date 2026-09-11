#include <jni.h>
#include <vector>

#include "lua_binding.hpp"

// (NativeEngine) responsible for the handle's lifetime, same idea, native, instead of java side

using scut::script::LuaEngine;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_scut_engine_NativeEngine_nativeCreateEngine(JNIEnv*, jobject) {
    return reinterpret_cast<jlong>(new LuaEngine());
}

JNIEXPORT void JNICALL
Java_com_scut_engine_NativeEngine_nativeDestroyEngine(JNIEnv*, jobject, jlong handle) {
    delete reinterpret_cast<LuaEngine*>(handle);
}

JNIEXPORT jboolean JNICALL
Java_com_scut_engine_NativeEngine_nativeRunScript(JNIEnv* env, jobject, jlong handle, jstring source) {
    auto* engine = reinterpret_cast<LuaEngine*>(handle);
    const char* chars = env->GetStringUTFChars(source, nullptr);
    bool ok = engine->run(chars);
    env->ReleaseStringUTFChars(source, chars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_scut_engine_NativeEngine_nativeTriggerHotkey(JNIEnv* env, jobject, jlong handle, jstring name) {
    auto* engine = reinterpret_cast<LuaEngine*>(handle);
    const char* chars = env->GetStringUTFChars(name, nullptr);
    engine->triggerHotkey(chars);
    env->ReleaseStringUTFChars(name, chars);
}

JNIEXPORT void JNICALL
Java_com_scut_engine_NativeEngine_nativeTick(JNIEnv*, jobject, jlong handle, jfloat dt) {
    reinterpret_cast<LuaEngine*>(handle)->tick(dt);
}

JNIEXPORT jbyteArray JNICALL
Java_com_scut_engine_NativeEngine_nativeGetWarpTexture(JNIEnv* env, jobject, jlong handle, jint width, jint height) {
    auto* engine = reinterpret_cast<LuaEngine*>(handle);
    std::vector<unsigned char> rgba = engine->gridWarp().encode_rgba(width, height);

    jbyteArray out = env->NewByteArray(static_cast<jsize>(rgba.size()));
    if (out != nullptr && !rgba.empty()) {
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(rgba.size()),
                                 reinterpret_cast<const jbyte*>(rgba.data()));
    }
    return out;
}

JNIEXPORT jint JNICALL
Java_com_scut_engine_NativeEngine_nativeNodeCount(JNIEnv*, jobject, jlong handle) {
    return static_cast<jint>(reinterpret_cast<LuaEngine*>(handle)->graph().all().size());
}

}  // extern "C"
