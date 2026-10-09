package com.scut.media;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.view.Surface;

import java.io.IOException;
import java.nio.ByteBuffer;

final class VideoWorker implements Runnable {

    private final Context ctx;
    private final Exporter.Job job;
    private final Exporter.Control ctl;
    private final MuxerGate gate;

    private MediaCodec encoder;
    private Surface encoderInput;
    private FrameGl gl;
    private EncoderOutput output;
    private FrameSelector selector;

    VideoWorker(Context ctx, Exporter.Job job, Exporter.Control ctl, MuxerGate gate) {
        this.ctx = ctx;
        this.job = job;
        this.ctl = ctl;
        this.gate = gate;
    }

    @Override
    public void run() {
        try {
            encode();
        } catch (Exporter.StopException e) {
            gate.abort();
        } catch (Throwable t) {
            ctl.fail(t);
            gate.abort();
        } finally {
            cleanup();
        }
    }

    private void encode() throws Exception {
        selector = new FrameSelector(job.fps);

        MediaFormat f = MediaFormat.createVideoFormat("video/avc", job.width, job.height);
        f.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        f.setInteger(MediaFormat.KEY_BIT_RATE, job.videoBitrate);
        f.setInteger(MediaFormat.KEY_FRAME_RATE, job.fps);
        f.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

        encoder = MediaCodec.createEncoderByType("video/avc");
        encoder.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoderInput = encoder.createInputSurface();
        encoder.start();
        output = new EncoderOutput(encoder, gate, ctl);
        gl = new FrameGl(encoderInput);

        long offset = 0;
        for (Exporter.Segment s : job.segments) {
            ctl.check();
            decodeSegment(s, offset);
            offset += s.outUs - s.inUs;
        }
        ctl.check();
        encoder.signalEndOfInputStream();
        output.drain(true);
    }

    private void decodeSegment(Exporter.Segment s, long offsetUs) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec dec = null;
        try {
            ex.setDataSource(ctx, s.uri, null);
            int track = pickTrack(ex, "video/");
            if (track < 0) {
                throw new IOException(s.name + " has no video");
            }
            MediaFormat fmt = ex.getTrackFormat(track);
            ex.selectTrack(track);
            ex.seekTo(s.inUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

            dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            dec.configure(fmt, gl.decoderSurface(), null, 0);
            dec.start();

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false;
            boolean done = false;
            // feed a second past the end, b frames need it to finish the last frames
            long feedLimit = s.outUs + 1000000L;
            long lastAlive = System.currentTimeMillis();

            while (!done) {
                ctl.check();
                if (!inputDone) {
                    int ii = dec.dequeueInputBuffer(10000);
                    if (ii >= 0) {
                        ByteBuffer buf = dec.getInputBuffer(ii);
                        int n = buf == null ? -1 : ex.readSampleData(buf, 0);
                        long t = ex.getSampleTime();
                        if (n < 0 || t > feedLimit) {
                            dec.queueInputBuffer(ii, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            dec.queueInputBuffer(ii, 0, n, t, 0);
                            ex.advance();
                        }
                        lastAlive = System.currentTimeMillis();
                    }
                }

                int oi = dec.dequeueOutputBuffer(info, 10000);
                if (oi >= 0) {
                    lastAlive = System.currentTimeMillis();
                    long pts = info.presentationTimeUs;
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    long outPts = offsetUs + pts - s.inUs;
                    boolean render = info.size > 0 && pts >= s.inUs && pts < s.outUs && selector.accept(outPts);
                    dec.releaseOutputBuffer(oi, render);
                    if (render) {
                        gl.awaitFrame();
                        gl.draw(job.width, job.height, s.dispW, s.dispH);
                        gl.present(outPts * 1000L);
                        ctl.doneUs = Math.max(ctl.doneUs, outPts);
                        output.drain(false);
                    }
                    if (eos || pts >= s.outUs) {
                        done = true;
                    }
                } else if (System.currentTimeMillis() - lastAlive > 10000) {
                    throw new IOException("decoder stalled on " + s.name);
                }
            }
        } finally {
            if (dec != null) {
                try {
                    dec.stop();
                } catch (RuntimeException ignored) {
                }
                try {
                    dec.release();
                } catch (RuntimeException ignored) {
                }
            }
            ex.release();
        }
    }

    static int pickTrack(MediaExtractor ex, String prefix) {
        for (int i = 0; i < ex.getTrackCount(); i++) {
            String mime = ex.getTrackFormat(i).getString(MediaFormat.KEY_MIME);
            if (mime != null && mime.startsWith(prefix)) {
                return i;
            }
        }
        return -1;
    }

    private void cleanup() {
        if (gl != null) {
            try {
                gl.release();
            } catch (RuntimeException ignored) {
            }
        }
        if (encoder != null) {
            try {
                encoder.stop();
            } catch (RuntimeException ignored) {
            }
            try {
                encoder.release();
            } catch (RuntimeException ignored) {
            }
        }
        if (encoderInput != null) {
            encoderInput.release();
        }
    }
}
