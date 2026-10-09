package com.scut.media;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class AudioWorker implements Runnable {

    private static final String MIME = "audio/mp4a-latm";
    private static final int CHUNK = 4096;

    private final Context ctx;
    private final Exporter.Job job;
    private final Exporter.Control ctl;
    private final MuxerGate gate;
    private final int rate;

    private MediaCodec encoder;
    private EncoderOutput output;
    private long framesOut;

    private final class Limit implements PcmConverter.Sink {
        private final long target;
        private long made;

        Limit(long target) {
            this.target = target;
        }

        @Override
        public void frames(short[] stereo, int n) {
            int use = (int) Math.min(n, target - made);
            if (use <= 0) {
                return;
            }
            try {
                writePcm(stereo, use);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
            made += use;
        }

        void silence(long count) throws Exception {
            long left = Math.min(count, target - made);
            short[] zeros = new short[CHUNK * 2];
            while (left > 0) {
                int n = (int) Math.min(left, CHUNK);
                writePcm(zeros, n);
                made += n;
                left -= n;
            }
        }

        long left() {
            return target - made;
        }

        boolean full() {
            return made >= target;
        }
    }

    AudioWorker(Context ctx, Exporter.Job job, Exporter.Control ctl, MuxerGate gate) {
        this.ctx = ctx;
        this.job = job;
        this.ctl = ctl;
        this.gate = gate;
        this.rate = job.audioRate;
    }

    @Override
    public void run() {
        try {
            encodeAll();
        } catch (Exporter.StopException e) {
            gate.abort();
        } catch (Throwable t) {
            ctl.fail(t);
            gate.abort();
        } finally {
            cleanup();
        }
    }

    private void encodeAll() throws Exception {
        MediaFormat f = MediaFormat.createAudioFormat(MIME, rate, 2);
        f.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        f.setInteger(MediaFormat.KEY_BIT_RATE, ExportSettings.AUDIO_BITRATE);
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024);
        encoder = MediaCodec.createEncoderByType(MIME);
        encoder.configure(f, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();
        output = new EncoderOutput(encoder, gate, ctl);

        for (Exporter.Segment s : job.segments) {
            ctl.check();
            segment(s);
        }

        while (true) {
            ctl.check();
            int ii = encoder.dequeueInputBuffer(10000);
            if (ii >= 0) {
                encoder.queueInputBuffer(ii, 0, 0, framesToUs(framesOut), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                break;
            }
            output.drain(false);
        }
        output.drain(true);
    }

    private void segment(Exporter.Segment s) throws Exception {
        Limit sink = new Limit(usToFrames(s.outUs - s.inUs));
        if (s.hasAudio) {
            try {
                decode(s, sink);
            } catch (Exporter.StopException e) {
                throw e;
            } catch (Exception e) {
                // bad audio shouldn't kill the whole export, silence fills the gap
                ctl.check();
            }
        }
        sink.silence(sink.left());
    }

    private void decode(Exporter.Segment s, Limit sink) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec dec = null;
        try {
            ex.setDataSource(ctx, s.uri, null);
            int track = VideoWorker.pickTrack(ex, "audio/");
            if (track < 0) {
                return;
            }
            MediaFormat fmt = ex.getTrackFormat(track);
            ex.selectTrack(track);
            ex.seekTo(s.inUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC);

            dec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            dec.configure(fmt, null, null, 0);
            dec.start();

            int srcRate = intOf(fmt, MediaFormat.KEY_SAMPLE_RATE, 44100);
            int srcCh = intOf(fmt, MediaFormat.KEY_CHANNEL_COUNT, 2);
            PcmConverter conv = new PcmConverter(srcRate, srcCh, rate, s.volume);

            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            short[] pcm = new short[0];
            boolean inputDone = false;
            boolean done = false;
            boolean started = false;
            long feedLimit = s.outUs + 500000L;
            long lastAlive = System.currentTimeMillis();

            while (!done && !sink.full()) {
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
                if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = dec.getOutputFormat();
                    srcRate = intOf(of, MediaFormat.KEY_SAMPLE_RATE, srcRate);
                    srcCh = intOf(of, MediaFormat.KEY_CHANNEL_COUNT, srcCh);
                    conv = new PcmConverter(srcRate, srcCh, rate, s.volume);
                } else if (oi >= 0) {
                    lastAlive = System.currentTimeMillis();
                    ByteBuffer ob = dec.getOutputBuffer(oi);
                    boolean eos = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (info.size > 0 && ob != null) {
                        int ch = Math.max(1, srcCh);
                        int frames = info.size / (2 * ch);
                        long startUs = info.presentationTimeUs;
                        long endUs = startUs + frames * 1000000L / srcRate;
                        if (endUs > s.inUs && startUs < s.outUs) {
                            int skip = startUs < s.inUs ? (int) ((s.inUs - startUs) * srcRate / 1000000L) : 0;
                            int cut = endUs > s.outUs ? (int) ((endUs - s.outUs) * srcRate / 1000000L) : 0;
                            int use = frames - skip - cut;
                            if (!started) {
                                started = true;
                                long gap = startUs - s.inUs;
                                if (gap > 5000) {
                                    sink.silence(usToFrames(gap));
                                }
                            }
                            if (use > 0) {
                                if (pcm.length < use * ch) {
                                    pcm = new short[use * ch];
                                }
                                ob.limit(info.offset + info.size);
                                ob.position(info.offset + skip * ch * 2);
                                ob.order(ByteOrder.nativeOrder()).asShortBuffer().get(pcm, 0, use * ch);
                                conv.convert(pcm, use, sink);
                            }
                        }
                        if (startUs >= s.outUs) {
                            done = true;
                        }
                    }
                    dec.releaseOutputBuffer(oi, false);
                    if (eos) {
                        done = true;
                    }
                } else if (System.currentTimeMillis() - lastAlive > 10000) {
                    throw new IOException("audio decoder stalled on " + s.name);
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

    private void writePcm(short[] stereo, int frames) throws Exception {
        int off = 0;
        while (off < frames) {
            ctl.check();
            int ii = encoder.dequeueInputBuffer(10000);
            if (ii < 0) {
                output.drain(false);
                continue;
            }
            ByteBuffer ib = encoder.getInputBuffer(ii);
            if (ib == null) {
                throw new IOException("audio encoder gave no buffer");
            }
            ib.clear();
            int n = Math.min(ib.capacity() / 4, frames - off);
            ib.order(ByteOrder.nativeOrder()).asShortBuffer().put(stereo, off * 2, n * 2);
            encoder.queueInputBuffer(ii, 0, n * 4, framesToUs(framesOut), 0);
            framesOut += n;
            off += n;
            output.drain(false);
        }
    }

    private long usToFrames(long us) {
        return (us * rate + 500000L) / 1000000L;
    }

    private long framesToUs(long frames) {
        return frames * 1000000L / rate;
    }

    private static int intOf(MediaFormat f, String key, int fallback) {
        if (!f.containsKey(key)) {
            return fallback;
        }
        try {
            return f.getInteger(key);
        } catch (ClassCastException e) {
            return fallback;
        }
    }

    private void cleanup() {
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
    }
}
