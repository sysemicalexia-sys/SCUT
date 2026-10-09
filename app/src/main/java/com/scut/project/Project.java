package com.scut.project;

import java.util.ArrayList;
import java.util.List;

public final class Project {

    public String id;
    public String name;
    public long created;
    public long modified;
    public int exportSide = 1080;
    public int exportFps = 30;
    public int exportQuality = 1;
    public final List<Clip> clips = new ArrayList<Clip>();

    public long totalMs() {
        return Timeline.total(clips);
    }

    public float aspect() {
        if (clips.isEmpty()) {
            return 16f / 9f;
        }
        Clip first = clips.get(0);
        if (first.width <= 0 || first.height <= 0) {
            return 16f / 9f;
        }
        return first.width / (float) first.height;
    }

    public boolean hasAudio() {
        for (Clip c : clips) {
            if (c.hasAudio) {
                return true;
            }
        }
        return false;
    }
}
