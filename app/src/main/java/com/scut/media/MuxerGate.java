package com.scut.media;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.nio.ByteBuffer;

final class MuxerGate {

    private final MediaMuxer muxer;
    private final int expected;
    private int added;
    private boolean started;
    private boolean aborted;

    MuxerGate(MediaMuxer muxer, int expected) {
        this.muxer = muxer;
        this.expected = expected;
    }

    synchronized int addTrack(MediaFormat format) {
        int i = muxer.addTrack(format);
        added++;
        if (added == expected) {
            muxer.start();
            started = true;
            notifyAll();
        }
        return i;
    }

    synchronized void write(int track, ByteBuffer data, MediaCodec.BufferInfo info) throws InterruptedException {
        while (!started && !aborted) {
            wait();
        }
        if (aborted) {
            throw new IllegalStateException("export stopped");
        }
        muxer.writeSampleData(track, data, info);
    }

    synchronized void abort() {
        aborted = true;
        notifyAll();
    }

    synchronized boolean started() {
        return started;
    }
}
