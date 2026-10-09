package com.scut.media;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

final class FrameGl implements SurfaceTexture.OnFrameAvailableListener {

    // EGL_RECORDABLE_ANDROID, the encoder surface needs it
    private static final int EGL_RECORDABLE_ANDROID = 0x3142;

    private static final String VERTEX =
            "uniform mat4 uMvp;\n"
            + "uniform mat4 uTex;\n"
            + "attribute vec4 aPos;\n"
            + "attribute vec4 aTex;\n"
            + "varying vec2 vTex;\n"
            + "void main() {\n"
            + "  gl_Position = uMvp * aPos;\n"
            + "  vTex = (uTex * aTex).xy;\n"
            + "}\n";

    private static final String FRAGMENT =
            "#extension GL_OES_EGL_image_external : require\n"
            + "precision mediump float;\n"
            + "varying vec2 vTex;\n"
            + "uniform samplerExternalOES sTex;\n"
            + "void main() {\n"
            + "  gl_FragColor = texture2D(sTex, vTex);\n"
            + "}\n";

    private static final float[] QUAD = {-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f};
    private static final float[] UV = {0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f};

    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext context = EGL14.EGL_NO_CONTEXT;
    private EGLSurface window = EGL14.EGL_NO_SURFACE;
    private int program;
    private int texId;
    private int aPos;
    private int aTex;
    private int uMvp;
    private int uTex;
    private SurfaceTexture texture;
    private Surface decoderSurface;
    private HandlerThread callbackThread;
    private final Object lock = new Object();
    private boolean frameReady;
    private final float[] mvp = new float[16];
    private final float[] texMatrix = new float[16];
    private final FloatBuffer quad;
    private final FloatBuffer uv;

    FrameGl(Surface encoderInput) throws IOException {
        quad = floats(QUAD);
        uv = floats(UV);
        try {
            setupEgl(encoderInput);
            setupGl();
        } catch (IOException e) {
            release();
            throw e;
        } catch (RuntimeException e) {
            release();
            throw e;
        }
    }

    Surface decoderSurface() {
        return decoderSurface;
    }

    private static FloatBuffer floats(float[] data) {
        FloatBuffer b = ByteBuffer.allocateDirect(data.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(data);
        b.position(0);
        return b;
    }

    private void setupEgl(Surface encoderInput) throws IOException {
        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == EGL14.EGL_NO_DISPLAY) {
            throw new IOException("no egl display");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw new IOException("egl init failed");
        }
        int[] attribs = {
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL_RECORDABLE_ANDROID, 1,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        if (!EGL14.eglChooseConfig(display, attribs, 0, configs, 0, 1, count, 0) || count[0] < 1) {
            throw new IOException("no egl config for the encoder");
        }
        int[] ctxAttribs = {EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE};
        context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, ctxAttribs, 0);
        if (context == EGL14.EGL_NO_CONTEXT) {
            throw new IOException("egl context failed");
        }
        int[] surfAttribs = {EGL14.EGL_NONE};
        window = EGL14.eglCreateWindowSurface(display, configs[0], encoderInput, surfAttribs, 0);
        if (window == EGL14.EGL_NO_SURFACE) {
            throw new IOException("egl window failed");
        }
        if (!EGL14.eglMakeCurrent(display, window, window, context)) {
            throw new IOException("egl make current failed");
        }
    }

    private void setupGl() throws IOException {
        program = link(VERTEX, FRAGMENT);
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aTex = GLES20.glGetAttribLocation(program, "aTex");
        uMvp = GLES20.glGetUniformLocation(program, "uMvp");
        uTex = GLES20.glGetUniformLocation(program, "uTex");

        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        texId = tex[0];
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        callbackThread = new HandlerThread("scut-frames");
        callbackThread.start();
        texture = new SurfaceTexture(texId);
        texture.setOnFrameAvailableListener(this, new Handler(callbackThread.getLooper()));
        decoderSurface = new Surface(texture);
    }

    private static int link(String vs, String fs) throws IOException {
        int v = compile(GLES20.GL_VERTEX_SHADER, vs);
        int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] != GLES20.GL_TRUE) {
            String log = GLES20.glGetProgramInfoLog(p);
            GLES20.glDeleteProgram(p);
            throw new IOException("shader link failed: " + log);
        }
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);
        return p;
    }

    private static int compile(int type, String src) throws IOException {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(s);
            GLES20.glDeleteShader(s);
            throw new IOException("shader compile failed: " + log);
        }
        return s;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture t) {
        synchronized (lock) {
            frameReady = true;
            lock.notifyAll();
        }
    }

    void awaitFrame() throws IOException, InterruptedException {
        synchronized (lock) {
            long end = System.currentTimeMillis() + 4000;
            while (!frameReady) {
                long left = end - System.currentTimeMillis();
                if (left <= 0) {
                    throw new IOException("decoder never delivered a frame");
                }
                lock.wait(left);
            }
            frameReady = false;
        }
        texture.updateTexImage();
        texture.getTransformMatrix(texMatrix);
    }

    void draw(int outW, int outH, int srcW, int srcH) {
        GLES20.glViewport(0, 0, outW, outH);
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        float outAspect = outW / (float) outH;
        float srcAspect = srcW / (float) srcH;
        float sx = 1f;
        float sy = 1f;
        if (srcAspect > outAspect) {
            sy = outAspect / srcAspect;
        } else {
            sx = srcAspect / outAspect;
        }
        Matrix.setIdentityM(mvp, 0);
        Matrix.scaleM(mvp, 0, sx, sy, 1f);

        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);
        GLES20.glUniformMatrix4fv(uMvp, 1, false, mvp, 0);
        GLES20.glUniformMatrix4fv(uTex, 1, false, texMatrix, 0);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 8, quad);
        GLES20.glEnableVertexAttribArray(aTex);
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 8, uv);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aTex);
    }

    void present(long ptsNs) {
        EGLExt.eglPresentationTimeANDROID(display, window, ptsNs);
        EGL14.eglSwapBuffers(display, window);
    }

    void release() {
        if (display != EGL14.EGL_NO_DISPLAY) {
            try {
                if (program != 0) {
                    GLES20.glDeleteProgram(program);
                }
                if (texId != 0) {
                    GLES20.glDeleteTextures(1, new int[] {texId}, 0);
                }
            } catch (RuntimeException ignored) {
            }
            program = 0;
            texId = 0;
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (window != EGL14.EGL_NO_SURFACE) {
                EGL14.eglDestroySurface(display, window);
            }
            if (context != EGL14.EGL_NO_CONTEXT) {
                EGL14.eglDestroyContext(display, context);
            }
            EGL14.eglReleaseThread();
            EGL14.eglTerminate(display);
        }
        display = EGL14.EGL_NO_DISPLAY;
        context = EGL14.EGL_NO_CONTEXT;
        window = EGL14.EGL_NO_SURFACE;
        if (decoderSurface != null) {
            decoderSurface.release();
            decoderSurface = null;
        }
        if (texture != null) {
            texture.release();
            texture = null;
        }
        if (callbackThread != null) {
            callbackThread.quitSafely();
            callbackThread = null;
        }
    }
}
