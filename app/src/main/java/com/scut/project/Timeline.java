package com.scut.project;

import java.util.ArrayList;
import java.util.List;

public final class Timeline {

    public static final class Spot {
        public int index;
        public long offsetMs;
    }

    private Timeline() {
    }

    public static long total(List<Clip> clips) {
        long t = 0;
        for (Clip c : clips) {
            t += c.lengthMs();
        }
        return t;
    }

    public static long startOf(List<Clip> clips, int index) {
        long t = 0;
        for (int i = 0; i < index && i < clips.size(); i++) {
            t += clips.get(i).lengthMs();
        }
        return t;
    }

    public static Spot locate(List<Clip> clips, long timeMs) {
        if (clips.isEmpty()) {
            return null;
        }
        long total = total(clips);
        long t = Math.max(0, Math.min(timeMs, total));
        long start = 0;
        for (int i = 0; i < clips.size(); i++) {
            long len = clips.get(i).lengthMs();
            if (t < start + len || i == clips.size() - 1) {
                Spot s = new Spot();
                s.index = i;
                s.offsetMs = Math.max(0, Math.min(t - start, len));
                return s;
            }
            start += len;
        }
        return null;
    }

    public static boolean split(List<Clip> clips, int index, long offsetMs, long minMs) {
        if (index < 0 || index >= clips.size()) {
            return false;
        }
        Clip a = clips.get(index);
        if (offsetMs < minMs || a.lengthMs() - offsetMs < minMs) {
            return false;
        }
        Clip b = a.copy();
        b.id = Clip.newId();
        long cut = a.inMs + offsetMs;
        a.outMs = cut;
        b.inMs = cut;
        clips.add(index + 1, b);
        return true;
    }

    public static List<Clip> snapshot(List<Clip> clips) {
        List<Clip> out = new ArrayList<Clip>(clips.size());
        for (Clip c : clips) {
            out.add(c.copy());
        }
        return out;
    }

    public static void restore(List<Clip> target, List<Clip> snap) {
        target.clear();
        target.addAll(snapshot(snap));
    }
}
