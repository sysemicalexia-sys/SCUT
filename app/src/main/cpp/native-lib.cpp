#include <jni.h>
#include <GLES3/gl3.h>
#include <android/log.h>

// the handling pointer (the classic implementation of the handling pointer)
extern "C" JNIEXPORT jboolean JNICALL
Java_com_scut_engine_NativeVideoEngine_loadCustomShader(JNIEnv *env, jobject thiz, jstring shader_source) {
    __android_log_print(ANDROID_LOG_INFO, "SCUT", "GLSL load");
    return JNI_TRUE;
}
