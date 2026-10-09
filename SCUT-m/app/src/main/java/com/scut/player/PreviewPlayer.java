package com.scut.player;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.Uri;
import android.view.Surface;
import android.view.TextureView;

import com.scut.project.Clip;
import com.scut.project.Timeline;

import java.util.ArrayList;
import java.util.List;

public final class PreviewPlayer implements TextureView.SurfaceTextureListener {

    public interface Listener {
        void onTime(long timelineMs);

        void onState(boolean playing);

        void onClip(Clip clip);

        void onError(String message);
    }

    private final Context ctx;
    private final TextureView view;
    private final Listener listener;
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            tick();
        }
    };

    private List<Clip> clips = new ArrayList<Clip>();
    private Surface surface;
    private MediaPlayer mp;
    private String loadedUri;
    private String brokenUri;
    private String reportedClipId;
    private boolean prepared;
    private boolean seeking;
    private boolean playing;
    private boolean wantPlay;
    private int index = -1;
    private long pendingSeek = -1;
    private long lastTimelineMs;

    public PreviewPlayer(Context ctx, TextureView view, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.view = view;
        this.listener = listener;
        view.setSurfaceTextureListener(this);
        if (view.isAvailable()) {
            surface = new Surface(view.getSurfaceTexture());
        }
    }

    public void setClips(List<Clip> list, long timelineMs) {
        clips = list;
        seekTo(timelineMs);
    }

    public boolean isPlaying() {
        return wantPlay;
    }

    public void refresh() {
        reconcile();
    }

    public long time() {
        return lastTimelineMs;
    }

    public void seekTo(long timelineMs) {
        Timeline.Spot s = Timeline.locate(clips, timelineMs);
        if (s == null) {
            releasePlayer();
            index = -1;
            lastTimelineMs = 0;
            return;
        }
        Clip c = clips.get(s.index);
        index = s.index;
        lastTimelineMs = Math.max(0, Math.min(timelineMs, Timeline.total(clips)));
        pendingSeek = c.inMs + s.offsetMs;
        if (!c.id.equals(reportedClipId)) {
            reportedClipId = c.id;
            listener.onClip(c);
        }
        reconcile();
    }

    public void play() {
        if (clips.isEmpty()) {
            return;
        }
        if (lastTimelineMs >= Timeline.total(clips) - 30) {
            seekTo(0);
        }
        wantPlay = true;
        reconcile();
    }

    public void pause() {
        wantPlay = false;
        reconcile();
    }

    public void release() {
        wantPlay = false;
        playing = false;
        view.removeCallbacks(ticker);
        releasePlayer();
    }

    private void reconcile() {
        if (surface == null || clips.isEmpty() || index < 0 || index >= clips.size()) {
            return;
        }
        Clip c = clips.get(index);
        if (mp == null || !c.uri.equals(loadedUri)) {
            if (c.uri.equals(brokenUri)) {
                return;
            }
            load(c);
            return;
        }
        if (!prepared) {
            return;
        }
        mp.setVolume(c.volume, c.volume);
        if (pendingSeek >= 0 && !seeking) {
            long t = pendingSeek;
            pendingSeek = -1;
            seeking = true;
            mp.seekTo(t, MediaPlayer.SEEK_CLOSEST);
            return;
        }
        if (seeking) {
            return;
        }
        if (wantPlay && !playing) {
            mp.start();
            playing = true;
            listener.onState(true);
            view.removeCallbacks(ticker);
            view.postOnAnimation(ticker);
        } else if (!wantPlay && playing) {
            mp.pause();
            playing = false;
            view.removeCallbacks(ticker);
            listener.onState(false);
        }
    }

    private void load(Clip c) {
        releasePlayer();
        prepared = false;
        seeking = false;
        playing = false;
        final MediaPlayer me = new MediaPlayer();
        mp = me;
        loadedUri = c.uri;
        final String name = c.name;
        me.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer p) {
                if (p != mp) {
                    return;
                }
                prepared = true;
                reconcile();
            }
        });
        me.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer p) {
                if (p != mp) {
                    return;
                }
                seeking = false;
                reconcile();
            }
        });
        me.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer p) {
                if (p != mp || !playing) {
                    return;
                }
                advance();
            }
        });
        me.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer p, int what, int extra) {
                if (p == mp) {
                    fail(name);
                }
                return true;
            }
        });
        try {
            me.setSurface(surface);
            me.setDataSource(ctx, Uri.parse(c.uri));
            me.prepareAsync();
        } catch (Exception e) {
            fail(name);
        }
    }

    private void fail(String name) {
        brokenUri = loadedUri;
        releasePlayer();
        loadedUri = null;
        boolean was = wantPlay;
        wantPlay = false;
        playing = false;
        if (was) {
            listener.onState(false);
        }
        listener.onError("can't play " + name);
    }

    private void tick() {
        if (!playing || mp == null || !prepared || index < 0 || index >= clips.size()) {
            return;
        }
        int pos;
        try {
            pos = mp.getCurrentPosition();
        } catch (IllegalStateException e) {
            return;
        }
        Clip c = clips.get(index);
        if (pos >= c.outMs - 20) {
            advance();
            return;
        }
        lastTimelineMs = Timeline.startOf(clips, index) + Math.max(0, pos - c.inMs);
        listener.onTime(lastTimelineMs);
        view.postOnAnimation(ticker);
    }

    private void advance() {
        view.removeCallbacks(ticker);
        if (index + 1 < clips.size()) {
            Clip cur = clips.get(index);
            Clip next = clips.get(index + 1);
            index++;
            lastTimelineMs = Timeline.startOf(clips, index);
            reportedClipId = next.id;
            listener.onClip(next);
            // same file and no gap, keep playing without a seek
            boolean same = next.uri.equals(cur.uri) && Math.abs(next.inMs - cur.outMs) < 60;
            if (same && mp != null && prepared && playing) {
                view.postOnAnimation(ticker);
                return;
            }
            if (mp != null && playing) {
                mp.pause();
            }
            playing = false;
            pendingSeek = next.inMs;
            reconcile();
            return;
        }
        wantPlay = false;
        if (mp != null && playing) {
            mp.pause();
        }
        playing = false;
        lastTimelineMs = Timeline.total(clips);
        listener.onTime(lastTimelineMs);
        listener.onState(false);
    }

    private void releasePlayer() {
        MediaPlayer old = mp;
        mp = null;
        prepared = false;
        seeking = false;
        if (old != null) {
            try {
                old.release();
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture st, int w, int h) {
        surface = new Surface(st);
        reconcile();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture st, int w, int h) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture st) {
        releasePlayer();
        loadedUri = null;
        if (playing) {
            playing = false;
            view.removeCallbacks(ticker);
            listener.onState(false);
        }
        wantPlay = false;
        if (surface != null) {
            surface.release();
            surface = null;
        }
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture st) {
    }
}
