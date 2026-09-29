package com.scut.engine

class NativeEngine {

    @Volatile
    private var handle: Long = 0

    fun create() {
        check(handle == 0L) { "engine already created" }
        handle = nativeCreateEngine()
    }

    fun destroy() {
        val old = handle
        handle = 0
        if (old != 0L) nativeDestroyEngine(old)
    }

    // null means the script ran, otherwise the error message
    fun runScript(source: String): String? =
        nativeRunScript(requireHandle(), source.toByteArray(Charsets.UTF_8))

    fun triggerHotkey(name: String): String? = nativeTriggerHotkey(requireHandle(), name)

    fun tick(deltaSeconds: Float) = nativeTick(requireHandle(), deltaSeconds)

    fun warpTexture(width: Int, height: Int): ByteArray =
        nativeGetWarpTexture(requireHandle(), width, height)

    fun nodeCount(): Int = nativeNodeCount(requireHandle())

    private fun requireHandle(): Long {
        val current = handle
        check(current != 0L) { "engine is not running" }
        return current
    }

    private external fun nativeCreateEngine(): Long
    private external fun nativeDestroyEngine(handle: Long)
    private external fun nativeRunScript(handle: Long, source: ByteArray): String?
    private external fun nativeTriggerHotkey(handle: Long, name: String): String?
    private external fun nativeTick(handle: Long, dt: Float)
    private external fun nativeGetWarpTexture(handle: Long, width: Int, height: Int): ByteArray
    private external fun nativeNodeCount(handle: Long): Int

    companion object {
        init {
            System.loadLibrary("scut")
        }
    }
}
