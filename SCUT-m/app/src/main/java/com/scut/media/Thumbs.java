package com.scut.media;

import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.util.LruCache;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class Thumbs {

    private final class Job implements Runnable {
        final String key;
        final String uri;
        final long timeUs;
        final int w;
        final int h;
        final boolean shared;
        final Runnable done;

        Job(String key, String uri, long timeUs, int w, int h, boolean shared, Runnable done) {
            this.key = key;
            this.uri = uri;
            this.timeUs = timeUs;
            this.w = w;
            this.h = h;
            this.shared = shared;
            this.done = done;
        }

        @Override
        public void run() {
            Bitmap b = null;
            MediaMetadataRetriever own = null;
            try {
                MediaMetadataRetriever r;
                if (shared) {
                    r = retrieverFor(uri);
                } else {
                    own = new MediaMetadataRetriever();
                    own.setDataSource(ctxHolder.app, Uri.parse(uri));
                    r = own;
                }
                b = decode(r, timeUs, w, h);
            } catch (Throwable ignored) {
            } finally {
                if (own != null) {
                    own.release();
                }
            }
            if (b != null) {
                cache.put(key, b);
            } else {
                failed.add(key);
            }
            pending.remove(key);
            if (done != null) {
                main.post(done);
            }
        }
    }

    private static final class AppHolder {
        android.content.Context app;
    }

    private final Handler main;
    private final AppHolder ctxHolder = new AppHolder();
    private final LruCache<String, Bitmap> cache;
    private final Set<String> pending = Collections.synchronizedSet(new HashSet<String>());
    private final Set<String> failed = Collections.synchronizedSet(new HashSet<String>());
    private final ThreadPoolExecutor strip;
    private final ExecutorService grid = Executors.newFixedThreadPool(2);
    private final LinkedHashMap<String, MediaMetadataRetriever> retrievers =
            new LinkedHashMap<String, MediaMetadataRetriever>(4, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, MediaMetadataRetriever> e) {
                    if (size() > 3) {
                        try {
                            e.getValue().release();
                        } catch (RuntimeException ignored) {
                        }
                        return true;
                    }
                    return false;
                }
            };

    public Thumbs(Handler main) {
        this.main = main;
        long budget = Math.min(Runtime.getRuntime().maxMemory() / 8, 48L * 1024 * 1024);
        cache = new LruCache<String, Bitmap>((int) budget) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount();
            }
        };
        strip = new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<Runnable>(48), new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor ex) {
                // queue is full, drop the oldest request
                Runnable old = ex.getQueue().poll();
                if (old instanceof Job) {
                    pending.remove(((Job) old).key);
                }
                if (!ex.isShutdown()) {
                    ex.execute(r);
                }
            }
        });
    }

    public void attach(android.content.Context app) {
        ctxHolder.app = app.getApplicationContext();
    }

    public Bitmap peek(String key) {
        return cache.get(key);
    }

    public void put(String key, Bitmap b) {
        cache.put(key, b);
    }

    public void frame(String key, String uri, long timeUs, int size, Runnable done) {
        if (cache.get(key) != null || failed.contains(key) || !pending.add(key)) {
            return;
        }
        strip.execute(new Job(key, uri, timeUs, size, size, true, done));
    }

    public void poster(String key, String uri, long timeUs, int w, int h, Runnable done) {
        if (cache.get(key) != null || failed.contains(key) || !pending.add(key)) {
            return;
        }
        grid.execute(new Job(key, uri, timeUs, w, h, false, done));
    }

    public static Bitmap decodePoster(android.content.Context ctx, String uri, long timeUs, int w, int h) {
        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(ctx, Uri.parse(uri));
            return decode(r, timeUs, w, h);
        } catch (RuntimeException e) {
            return null;
        } finally {
            r.release();
        }
    }

    private MediaMetadataRetriever retrieverFor(String uri) {
        synchronized (retrievers) {
            MediaMetadataRetriever r = retrievers.get(uri);
            if (r == null) {
                r = new MediaMetadataRetriever();
                try {
                    r.setDataSource(ctxHolder.app, Uri.parse(uri));
                } catch (RuntimeException e) {
                    r.release();
                    throw e;
                }
                retrievers.put(uri, r);
            }
            return r;
        }
    }

    private static Bitmap decode(MediaMetadataRetriever r, long timeUs, int w, int h) {
        Bitmap src = r.getFrameAtTime(Math.max(0, timeUs), MediaMetadataRetriever.OPTION_CLOSEST_SYNC);
        if (src == null) {
            return null;
        }
        float target = w / (float) h;
        int sw = src.getWidth();
        int sh = src.getHeight();
        int cw;
        int ch;
        if (sw / (float) sh > target) {
            ch = sh;
            cw = Math.round(sh * target);
        } else {
            cw = sw;
            ch = Math.round(sw / target);
        }
        cw = Math.max(1, Math.min(cw, sw));
        ch = Math.max(1, Math.min(ch, sh));
        Bitmap cropped = Bitmap.createBitmap(src, (sw - cw) / 2, (sh - ch) / 2, cw, ch);
        Bitmap scaled = Bitmap.createScaledBitmap(cropped, w, h, true);
        if (scaled != cropped && cropped != src) {
            cropped.recycle();
        }
        if (scaled != src) {
            src.recycle();
        }
        return scaled;
    }
}
