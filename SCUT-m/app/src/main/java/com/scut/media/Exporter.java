package com.scut.media;

import android.content.Context;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class Exporter {

    public static final class Segment {
        public Uri uri;
        public String name;
        public long inUs;
        public long outUs;
        public float volume = 1f;
        public boolean hasAudio;
        public int dispW;
        public int dispH;
    }

    public static final class Job {
        public final List<Segment> segments = new ArrayList<Segment>();
        public int width;
        public int height;
        public int fps = 30;
        public int videoBitrate;
        public int audioRate = 44100;
        public File output;

        public long totalUs() {
            long t = 0;
            for (Segment s : segments) {
                t += s.outUs - s.inUs;
            }
            return t;
        }

        public boolean hasAudio() {
            for (Segment s : segments) {
                if (s.hasAudio) {
                    return true;
                }
            }
            return false;
        }
    }

    public interface Callback {
        void onProgress(float fraction);

        void onDone(File output);

        void onFailed(String message);

        void onCancelled();
    }

    static final class StopException extends RuntimeException {
        StopException() {
            super("export stopped");
        }
    }

    static final class Control {
        volatile boolean cancelled;
        volatile String error;
        volatile long doneUs;

        boolean stopped() {
            return cancelled || error != null;
        }

        void check() {
            if (stopped()) {
                throw new StopException();
            }
        }

        synchronized void fail(Throwable t) {
            if (error == null && !cancelled) {
                error = describe(t);
            }
        }

        private static String describe(Throwable t) {
            String m = t.getMessage();
            if (t instanceof IOException && m != null) {
                return m;
            }
            String n = t.getClass().getSimpleName();
            return m == null ? n : n + ": " + m;
        }
    }

    private final Context ctx;
    private final Job job;
    private final Callback cb;
    private final Control control = new Control();
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile MuxerGate gate;

    public Exporter(Context ctx, Job job, Callback cb) {
        this.ctx = ctx.getApplicationContext();
        this.job = job;
        this.cb = cb;
    }

    public void start() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                runAll();
            }
        }, "scut-export");
        t.start();
    }

    public void cancel() {
        control.cancelled = true;
        MuxerGate g = gate;
        if (g != null) {
            g.abort();
        }
    }

    private void runAll() {
        MediaMuxer muxer = null;
        try {
            File dir = job.output.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            muxer = new MediaMuxer(job.output.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            boolean audio = job.hasAudio();
            MuxerGate g = new MuxerGate(muxer, audio ? 2 : 1);
            gate = g;

            Thread video = new Thread(new VideoWorker(ctx, job, control, g), "scut-video");
            Thread sound = null;
            if (audio) {
                sound = new Thread(new AudioWorker(ctx, job, control, g), "scut-audio");
            }
            video.start();
            if (sound != null) {
                sound.start();
            }

            long total = Math.max(1, job.totalUs());
            while (video.isAlive()) {
                video.join(120);
                postProgress(Math.min(1f, control.doneUs / (float) total));
            }
            if (sound != null) {
                sound.join();
            }

            if (!control.stopped()) {
                if (!g.started()) {
                    throw new IOException("nothing was encoded");
                }
                muxer.stop();
            }
        } catch (Throwable t) {
            control.fail(t);
        } finally {
            if (muxer != null) {
                try {
                    muxer.release();
                } catch (RuntimeException ignored) {
                }
            }
        }
        finish();
    }

    private void finish() {
        if (control.cancelled) {
            job.output.delete();
            main.post(new Runnable() {
                @Override
                public void run() {
                    cb.onCancelled();
                }
            });
            return;
        }
        final String error = control.error;
        if (error != null || job.output.length() == 0) {
            job.output.delete();
            final String message = error != null ? error : "export produced an empty file";
            main.post(new Runnable() {
                @Override
                public void run() {
                    cb.onFailed(message);
                }
            });
            return;
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                cb.onProgress(1f);
                cb.onDone(job.output);
            }
        });
    }

    private void postProgress(final float p) {
        main.post(new Runnable() {
            @Override
            public void run() {
                cb.onProgress(p);
            }
        });
    }
}
