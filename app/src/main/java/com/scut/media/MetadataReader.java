package com.scut.media;

import android.content.Context;
import android.database.Cursor;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.OpenableColumns;

import com.scut.project.Clip;

import java.io.IOException;

public final class MetadataReader {

    // api 28 key, kept as a number so an older android.jar still compiles
    private static final int KEY_FRAME_COUNT = 32;

    private MetadataReader() {
    }

    public static VideoInfo read(Context ctx, Uri uri) throws IOException {
        VideoInfo v = new VideoInfo();
        v.uri = uri.toString();
        v.name = "video";
        nameAndSize(ctx, uri, v);

        MediaMetadataRetriever r = new MediaMetadataRetriever();
        try {
            r.setDataSource(ctx, uri);
            if (!"yes".equals(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))) {
                throw new IOException(v.name + " has no video track");
            }
            v.durationMs = num(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
            int w = (int) num(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));
            int h = (int) num(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT));
            v.rotation = (int) num(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION));
            boolean turned = v.rotation == 90 || v.rotation == 270;
            v.width = turned ? h : w;
            v.height = turned ? w : h;
            v.bitrate = (int) num(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE));
            v.hasAudio = "yes".equals(r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO));
            long frames = num(r.extractMetadata(KEY_FRAME_COUNT));
            if (frames > 0 && v.durationMs > 0) {
                v.fps = frames * 1000f / v.durationMs;
            }
        } catch (RuntimeException e) {
            throw new IOException("can't read " + v.name);
        } finally {
            r.release();
        }

        if (v.durationMs <= 0 || v.width <= 0 || v.height <= 0) {
            throw new IOException(v.name + " looks broken");
        }

        MediaExtractor ex = new MediaExtractor();
        try {
            ex.setDataSource(ctx, uri, null);
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime == null) {
                    continue;
                }
                if (mime.startsWith("video/") && v.videoMime == null) {
                    v.videoMime = mime;
                    if (v.fps <= 0f && f.containsKey(MediaFormat.KEY_FRAME_RATE)) {
                        v.fps = number(f, MediaFormat.KEY_FRAME_RATE);
                    }
                } else if (mime.startsWith("audio/") && v.audioMime == null) {
                    v.audioMime = mime;
                    v.hasAudio = true;
                    if (f.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                        v.sampleRate = (int) number(f, MediaFormat.KEY_SAMPLE_RATE);
                    }
                    if (f.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) {
                        v.channels = (int) number(f, MediaFormat.KEY_CHANNEL_COUNT);
                    }
                }
            }
        } catch (IOException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IOException("can't open " + v.name);
        } finally {
            ex.release();
        }
        if (v.videoMime == null) {
            throw new IOException(v.name + " has no video track");
        }
        if (v.fps <= 0f) {
            v.fps = 30f;
        }
        return v;
    }

    public static Clip toClip(VideoInfo v) {
        Clip c = new Clip();
        c.id = Clip.newId();
        c.uri = v.uri;
        c.name = v.name;
        c.sourceMs = v.durationMs;
        c.width = v.width;
        c.height = v.height;
        c.fps = v.fps;
        c.inMs = 0;
        c.outMs = v.durationMs;
        c.hasAudio = v.hasAudio;
        c.sampleRate = v.sampleRate;
        c.bitrate = v.bitrate;
        c.videoMime = v.videoMime;
        c.audioMime = v.audioMime;
        c.sizeBytes = v.sizeBytes;
        return c;
    }

    private static void nameAndSize(Context ctx, Uri uri, VideoInfo v) {
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(uri,
                    new String[] {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE}, null, null, null);
            if (c != null && c.moveToFirst()) {
                String n = c.getString(0);
                if (n != null) {
                    v.name = n;
                }
                v.sizeBytes = c.getLong(1);
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    private static long num(String s) {
        if (s == null) {
            return 0;
        }
        try {
            return Long.parseLong(s.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static float number(MediaFormat f, String key) {
        try {
            return f.getInteger(key);
        } catch (ClassCastException e) {
            return f.getFloat(key);
        }
    }
}
