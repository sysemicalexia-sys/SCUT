package com.scut.engine;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

// keeps the engine for the whole process, scripts run on one thread so a slow one can't freeze the ui
public final class EngineController {

    public interface Listener {
        void onStatus(String status);
    }

    private static final String TAG = "SCUT";
    private static final long MAX_SCRIPT_BYTES = 1L << 20;

    private static EngineController instance;

    public static synchronized EngineController get() {
        if (instance == null) {
            instance = new EngineController();
        }
        return instance;
    }

    public static synchronized void release() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private volatile NativeEngine engine;
    private volatile String status = "Engine starting";
    private volatile Listener listener;

    private EngineController() {
        try {
            NativeEngine created = new NativeEngine();
            created.create();
            engine = created;
            status = "Engine ready \u00b7 " + created.nodeCount() + " nodes";
        } catch (LinkageError e) {
            Log.e(TAG, "native library unavailable", e);
            status = "Native library could not be loaded on this device";
        } catch (RuntimeException e) {
            Log.e(TAG, "engine failed to start", e);
            status = "Engine failed to start: " + e.getMessage();
        }
    }

    public String getStatus() {
        return status;
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void runScript(final File file) {
        final NativeEngine current = engine;
        if (current == null) {
            publish("Engine is not running");
            return;
        }
        publish("Running " + file.getName() + "...");
        try {
            worker.execute(new Runnable() {
                @Override
                public void run() {
                    publish(execute(current, file));
                }
            });
        } catch (RejectedExecutionException e) {
            publish("Engine is shutting down");
        }
    }

    private String execute(NativeEngine target, File file) {
        String name = file.getName();
        if (file.length() > MAX_SCRIPT_BYTES) {
            return name + ": larger than 1 MB";
        }
        String source;
        try {
            source = readUtf8(file);
        } catch (IOException e) {
            return name + ": cannot read file";
        }
        try {
            String error = target.runScript(source);
            if (error == null) {
                return name + ": ok \u00b7 " + target.nodeCount() + " nodes";
            }
            return name + ": failed - " + error;
        } catch (RuntimeException e) {
            return name + ": " + e.getMessage();
        }
    }

    private static String readUtf8(File file) throws IOException {
        InputStream in = new FileInputStream(file);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    private void publish(final String text) {
        status = text;
        main.post(new Runnable() {
            @Override
            public void run() {
                Listener l = listener;
                if (l != null) {
                    l.onStatus(text);
                }
            }
        });
    }

    private void shutdown() {
        listener = null;
        worker.shutdownNow();
        NativeEngine current = engine;
        engine = null;
        if (current != null) {
            current.destroy();
        }
    }
}
