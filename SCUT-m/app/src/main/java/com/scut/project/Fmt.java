package com.scut.project;

import java.util.Locale;

public final class Fmt {

    private Fmt() {
    }

    public static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        long h = s / 3600;
        long m = (s / 60) % 60;
        long sec = s % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        }
        return String.format(Locale.US, "%02d:%02d", m, sec);
    }

    public static String badge(long ms) {
        long s = Math.max(0, ms) / 1000;
        long h = s / 3600;
        long m = (s / 60) % 60;
        long sec = s % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, sec);
        }
        return String.format(Locale.US, "%d:%02d", m, sec);
    }

    public static String length(long ms) {
        if (ms < 60000) {
            return String.format(Locale.US, "%.1fs", ms / 1000f);
        }
        return badge(ms);
    }

    public static String size(long bytes) {
        if (bytes >= 1L << 30) {
            return String.format(Locale.US, "%.1f GB", bytes / (double) (1L << 30));
        }
        if (bytes >= 1L << 20) {
            return String.format(Locale.US, "%.1f MB", bytes / (double) (1L << 20));
        }
        return String.format(Locale.US, "%d KB", Math.max(1, bytes >> 10));
    }

    public static String rate(long bitsPerSecond) {
        if (bitsPerSecond >= 1000000) {
            return String.format(Locale.US, "%.1f Mbps", bitsPerSecond / 1000000.0);
        }
        return String.format(Locale.US, "%d kbps", bitsPerSecond / 1000);
    }
}
