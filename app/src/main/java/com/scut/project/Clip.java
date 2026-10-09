package com.scut.project;

import java.util.UUID;

public final class Clip {

    public String id;
    public String uri;
    public String name;
    public long sourceMs;
    public int width;
    public int height;
    public float fps;
    public long inMs;
    public long outMs;
    public float volume = 1f;
    public boolean hasAudio;
    public int sampleRate;
    public int bitrate;
    public String videoMime;
    public String audioMime;
    public long sizeBytes;

    public static String newId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public long lengthMs() {
        return outMs - inMs;
    }

    public Clip copy() {
        Clip c = new Clip();
        c.id = id;
        c.uri = uri;
        c.name = name;
        c.sourceMs = sourceMs;
        c.width = width;
        c.height = height;
        c.fps = fps;
        c.inMs = inMs;
        c.outMs = outMs;
        c.volume = volume;
        c.hasAudio = hasAudio;
        c.sampleRate = sampleRate;
        c.bitrate = bitrate;
        c.videoMime = videoMime;
        c.audioMime = audioMime;
        c.sizeBytes = sizeBytes;
        return c;
    }
}
