package com.scut.engine

// owns app/src/main/cpp/lua_binding.hpp
// create() & destroy() must each run exactly once see MainActivity.
class NativeEngine {

    private var handle: Long = 0

    fun create() {
        check(handle == 0L) { "NativeEngine.create() called twice" }
        handle = nativeCreateEngine()
    }

    fun destroy() {
        if (handle != 0L) {
            nativeDestroyEngine(handle)
            handle = 0
        }
    }

    fun runScript(source: String): Boolean = nativeRunScript(handle, source)
    fun triggerHotkey(name: String) = nativeTriggerHotkey(handle, name)
    fun tick(deltaSeconds: Float) = nativeTick(handle, deltaSeconds)
    fun warpTexture(width: Int, height: Int): ByteArray = nativeGetWarpTexture(handle, width, height)
    fun nodeCount(): Int = nativeNodeCount(handle)

    private external fun nativeCreateEngine(): Long
    private external fun nativeDestroyEngine(handle: Long)
    private external fun nativeRunScript(handle: Long, source: String): Boolean
    private external fun nativeTriggerHotkey(handle: Long, name: String)
    private external fun nativeTick(handle: Long, dt: Float)
    private external fun nativeGetWarpTexture(handle: Long, width: Int, height: Int): ByteArray
    private external fun nativeNodeCount(handle: Long): Int

    companion object {
        init { System.loadLibrary("scut") }
    }
}
