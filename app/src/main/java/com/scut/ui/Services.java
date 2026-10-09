package com.scut.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;

import com.scut.media.Thumbs;
import com.scut.project.Clip;
import com.scut.project.Project;
import com.scut.project.ProjectStore;

import java.io.IOException;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class Services {

    private static Services instance;

    public final Context app;
    public final Handler main;
    public final ExecutorService io;
    public final ExecutorService disk;
    public volatile Runnable onPoster;
    public final ProjectStore store;
    public final Thumbs thumbs;

    private Services(Context context) {
        app = context.getApplicationContext();
        main = new Handler(Looper.getMainLooper());
        io = Executors.newFixedThreadPool(2);
        disk = Executors.newSingleThreadExecutor();
        store = new ProjectStore(app);
        thumbs = new Thumbs(main);
        thumbs.attach(app);
    }

    public void saveAsync(Project p) {
        final String id = p.id;
        final String json;
        try {
            json = store.encode(p);
        } catch (IOException e) {
            return;
        }
        disk.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    store.write(id, json);
                } catch (IOException ignored) {
                }
            }
        });
    }

    public void refreshPoster(Project p) {
        if (p.clips.isEmpty()) {
            return;
        }
        Clip first = p.clips.get(0);
        final String id = p.id;
        final String uri = first.uri;
        final long timeUs = first.inMs * 1000L;
        io.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap b = Thumbs.decodePoster(app, uri, timeUs, 360, 450);
                if (b != null) {
                    store.savePoster(id, b);
                    b.recycle();
                    Runnable r = onPoster;
                    if (r != null) {
                        main.post(r);
                    }
                }
            }
        });
    }

    public static synchronized Services get(Context context) {
        if (instance == null) {
            instance = new Services(context);
        }
        return instance;
    }
}
