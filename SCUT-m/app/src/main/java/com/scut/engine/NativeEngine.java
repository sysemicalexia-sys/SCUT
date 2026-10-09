package com.scut.engine;

import java.nio.charset.Charset;

public final class NativeEngine {

    static {
        System.loadLibrary("scut");
    }

    private static final Charset UTF8 = Charset.forName("UTF-8");

    private volatile long handle;

    public void create() {
        if (handle != 0L) {
            throw new IllegalStateException("engine already created");
        }
        handle = nativeCreateEngine();
    }

    public void destroy() {
        long old = handle;
        handle = 0L;
        if (old != 0L) {
            nativeDestroyEngine(old);
        }
    }

    // null when the script ran fine, else the error text
    public String runScript(String source) {
        return nativeRunScript(requireHandle(), source.getBytes(UTF8));
    }

    public String triggerHotkey(String name) {
        return nativeTriggerHotkey(requireHandle(), name);
    }

    public void tick(float deltaSeconds) {
        nativeTick(requireHandle(), deltaSeconds);
    }

    public byte[] warpTexture(int width, int height) {
        return nativeGetWarpTexture(requireHandle(), width, height);
    }

    public int nodeCount() {
        return nativeNodeCount(requireHandle());
    }

    private long requireHandle() {
        long current = handle;
        if (current == 0L) {
            throw new IllegalStateException("engine is not running");
        }
        return current;
    }

    private native long nativeCreateEngine();

    private native void nativeDestroyEngine(long handle);

    private native String nativeRunScript(long handle, byte[] source);

    private native String nativeTriggerHotkey(long handle, String name);

    private native void nativeTick(long handle, float dt);

    private native byte[] nativeGetWarpTexture(long handle, int width, int height);

    private native int nativeNodeCount(long handle);
}
