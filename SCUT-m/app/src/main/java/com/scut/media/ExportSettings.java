package com.scut.media;

public final class ExportSettings {

    public static final int[] SIDES = {480, 720, 1080, 1440, 2160};
    public static final int[] FPS = {24, 25, 30, 50, 60};
    public static final String[] QUALITY = {"Low", "Recommended", "High"};
    public static final int AUDIO_BITRATE = 128000;

    private static final float[] BITS_PER_PIXEL = {0.07f, 0.10f, 0.15f};

    private ExportSettings() {
    }

    public static int[] canvas(float aspect, int side) {
        int w;
        int h;
        if (aspect >= 1f) {
            h = side;
            w = Math.round(side * aspect);
        } else {
            w = side;
            h = Math.round(side / aspect);
        }
        w = Math.max(2, w & ~1);
        h = Math.max(2, h & ~1);
        return new int[] {w, h};
    }

    public static int bitrate(int w, int h, int fps, int quality) {
        int q = Math.max(0, Math.min(quality, BITS_PER_PIXEL.length - 1));
        long bits = (long) (w * (long) h * fps * BITS_PER_PIXEL[q]);
        return (int) Math.max(400000L, Math.min(bits, 100000000L));
    }

    public static long estimateBytes(long durationMs, int videoBitrate, boolean audio) {
        long bps = videoBitrate + (audio ? AUDIO_BITRATE : 0);
        return bps / 8 * durationMs / 1000;
    }

    public static String sideLabel(int side) {
        if (side == 1440) {
            return "2K";
        }
        if (side == 2160) {
            return "4K";
        }
        return side + "P";
    }
}
