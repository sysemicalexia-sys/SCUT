#include <jni.h>

#include <algorithm>
#include <memory>
#include <mutex>
#include <new>
#include <string>
#include <unordered_map>
#include <vector>

#include "lua_binding.hpp"

using scut::script::LuaEngine;
using scut::script::RunResult;

namespace {

// Handles handed to Kotlin are looked up here instead of being cast blindly,
// so a stale or zero handle throws instead of crashing. shared_ptr keeps an
// engine alive until any call still running on it returns. Leaked on purpose:
// no static destructors at process exit.
std::mutex& registryMutex() {
    static auto* m = new std::mutex();
    return *m;
}

std::unordered_map<LuaEngine*, std::shared_ptr<LuaEngine>>& registry() {
    static auto* r = new std::unordered_map<LuaEngine*, std::shared_ptr<LuaEngine>>();
    return *r;
}

std::shared_ptr<LuaEngine> lookup(jlong handle) {
    std::lock_guard<std::mutex> lock(registryMutex());
    auto it = registry().find(reinterpret_cast<LuaEngine*>(handle));
    return it == registry().end() ? nullptr : it->second;
}

void throwJava(JNIEnv* env, const char* cls, const char* msg) {
    if (env->ExceptionCheck()) return;
    jclass c = env->FindClass(cls);
    if (c == nullptr) return;
    env->ThrowNew(c, msg);
    env->DeleteLocalRef(c);
}

// No C++ exception may cross the JNI boundary.
template <typename Fn>
auto guarded(JNIEnv* env, Fn&& fn) -> decltype(fn()) {
    using R = decltype(fn());
    try {
        return fn();
    } catch (const std::bad_alloc&) {
        throwJava(env, "java/lang/OutOfMemoryError", "native allocation failed");
    } catch (const std::exception& e) {
        throwJava(env, "java/lang/IllegalStateException", e.what());
    } catch (...) {
        throwJava(env, "java/lang/IllegalStateException", "unknown native error");
    }
    return R();
}

std::shared_ptr<LuaEngine> requireEngine(JNIEnv* env, jlong handle) {
    auto engine = lookup(handle);
    if (!engine) throwJava(env, "java/lang/IllegalStateException", "engine is not running");
    return engine;
}

// NewStringUTF aborts the VM (CheckJNI) on anything that is not valid
// modified UTF-8, and Lua error text can contain arbitrary script bytes.
std::string toJavaSafeUtf8(const std::string& in) {
    constexpr size_t kMaxBytes = 512;
    auto cont = [&](size_t i) {
        return i < in.size() && (static_cast<unsigned char>(in[i]) & 0xC0) == 0x80;
    };

    std::string out;
    size_t i = 0;
    while (i < in.size() && out.size() < kMaxBytes) {
        unsigned char c = static_cast<unsigned char>(in[i]);
        if (c == '\n' || c == '\t' || (c >= 0x20 && c < 0x7F)) {
            out += static_cast<char>(c);
            i += 1;
        } else if (c >= 0xC2 && c <= 0xDF && cont(i + 1)) {
            out.append(in, i, 2);
            i += 2;
        } else if (c >= 0xE0 && c <= 0xEF && cont(i + 1) && cont(i + 2) &&
                   !(c == 0xE0 && static_cast<unsigned char>(in[i + 1]) < 0xA0) &&
                   !(c == 0xED && static_cast<unsigned char>(in[i + 1]) >= 0xA0)) {
            out.append(in, i, 3);
            i += 3;
        } else if (c >= 0xF0 && c <= 0xF4 && cont(i + 1) && cont(i + 2) && cont(i + 3)) {
            out += '?';
            i += 4;
        } else {
            out += '?';
            i += 1;
        }
    }
    if (i < in.size()) out += "...";
    return out;
}

jstring toJavaError(JNIEnv* env, const RunResult& result) {
    if (result.ok) return nullptr;
    return env->NewStringUTF(toJavaSafeUtf8(result.error).c_str());
}

struct Utf8Chars {
    JNIEnv* env;
    jstring str;
    const char* chars;

    Utf8Chars(JNIEnv* e, jstring s) : env(e), str(s), chars(e->GetStringUTFChars(s, nullptr)) {}
    ~Utf8Chars() {
        if (chars != nullptr) env->ReleaseStringUTFChars(str, chars);
    }
    Utf8Chars(const Utf8Chars&) = delete;
    Utf8Chars& operator=(const Utf8Chars&) = delete;
};

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_scut_engine_NativeEngine_nativeCreateEngine(JNIEnv* env, jobject) {
    return guarded(env, [&]() -> jlong {
        auto engine = std::make_shared<LuaEngine>();
        LuaEngine* key = engine.get();
        std::lock_guard<std::mutex> lock(registryMutex());
        registry().emplace(key, std::move(engine));
        return reinterpret_cast<jlong>(key);
    });
}

JNIEXPORT void JNICALL
Java_com_scut_engine_NativeEngine_nativeDestroyEngine(JNIEnv* env, jobject, jlong handle) {
    guarded(env, [&]() {
        std::shared_ptr<LuaEngine> doomed;
        {
            std::lock_guard<std::mutex> lock(registryMutex());
            auto it = registry().find(reinterpret_cast<LuaEngine*>(handle));
            if (it == registry().end()) return;
            doomed = std::move(it->second);
            registry().erase(it);
        }
    });
}

// Returns null on success, otherwise the error message.
JNIEXPORT jstring JNICALL
Java_com_scut_engine_NativeEngine_nativeRunScript(JNIEnv* env, jobject, jlong handle, jbyteArray source) {
    return guarded(env, [&]() -> jstring {
        auto engine = requireEngine(env, handle);
        if (!engine) return nullptr;
        if (source == nullptr) {
            throwJava(env, "java/lang/NullPointerException", "source");
            return nullptr;
        }

        jsize length = env->GetArrayLength(source);
        if (static_cast<size_t>(length) > LuaEngine::kMaxScriptBytes) {
            return env->NewStringUTF("script is larger than 1 MB");
        }

        std::string code(static_cast<size_t>(length), '\0');
        if (length > 0) {
            env->GetByteArrayRegion(source, 0, length, reinterpret_cast<jbyte*>(&code[0]));
            if (env->ExceptionCheck()) return nullptr;
        }

        RunResult result;
        {
            std::lock_guard<std::mutex> lock(engine->mutex());
            result = engine->run(code);
        }
        return toJavaError(env, result);
    });
}

JNIEXPORT jstring JNICALL
Java_com_scut_engine_NativeEngine_nativeTriggerHotkey(JNIEnv* env, jobject, jlong handle, jstring name) {
    return guarded(env, [&]() -> jstring {
        auto engine = requireEngine(env, handle);
        if (!engine) return nullptr;
        if (name == nullptr) {
            throwJava(env, "java/lang/NullPointerException", "name");
            return nullptr;
        }

        Utf8Chars chars(env, name);
        if (chars.chars == nullptr) return nullptr;

        RunResult result;
        {
            std::lock_guard<std::mutex> lock(engine->mutex());
            result = engine->triggerHotkey(chars.chars);
        }
        return toJavaError(env, result);
    });
}

JNIEXPORT void JNICALL
Java_com_scut_engine_NativeEngine_nativeTick(JNIEnv* env, jobject, jlong handle, jfloat dt) {
    guarded(env, [&]() {
        auto engine = requireEngine(env, handle);
        if (!engine) return;
        std::lock_guard<std::mutex> lock(engine->mutex());
        engine->tick(dt);
    });
}

JNIEXPORT jbyteArray JNICALL
Java_com_scut_engine_NativeEngine_nativeGetWarpTexture(JNIEnv* env, jobject, jlong handle, jint width, jint height) {
    return guarded(env, [&]() -> jbyteArray {
        auto engine = requireEngine(env, handle);
        if (!engine) return nullptr;

        std::vector<unsigned char> rgba;
        {
            std::lock_guard<std::mutex> lock(engine->mutex());
            rgba = engine->gridWarp().encode_rgba(width, height);
        }

        jbyteArray out = env->NewByteArray(static_cast<jsize>(rgba.size()));
        if (out != nullptr && !rgba.empty()) {
            env->SetByteArrayRegion(out, 0, static_cast<jsize>(rgba.size()),
                                    reinterpret_cast<const jbyte*>(rgba.data()));
        }
        return out;
    });
}

JNIEXPORT jint JNICALL
Java_com_scut_engine_NativeEngine_nativeNodeCount(JNIEnv* env, jobject, jlong handle) {
    return guarded(env, [&]() -> jint {
        auto engine = requireEngine(env, handle);
        if (!engine) return 0;
        std::lock_guard<std::mutex> lock(engine->mutex());
        return static_cast<jint>(engine->graph().all().size());
    });
}

}  // extern "C"
