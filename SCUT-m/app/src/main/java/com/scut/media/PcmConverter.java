package com.scut.media;

public final class PcmConverter {

    public interface Sink {
        void frames(short[] stereo, int frameCount);
    }

    private final int channels;
    private final float gain;
    private final double step;
    private final boolean resample;
    private double pos;
    private float prevL;
    private float prevR;
    private short[] out = new short[4096];

    public PcmConverter(int srcRate, int srcChannels, int dstRate, float gain) {
        this.channels = Math.max(1, srcChannels);
        this.gain = gain;
        this.resample = srcRate != dstRate;
        this.step = srcRate / (double) dstRate;
    }

    public void convert(short[] in, int frames, Sink sink) {
        if (frames <= 0) {
            return;
        }
        if (!resample) {
            grow(frames);
            for (int i = 0; i < frames; i++) {
                out[i * 2] = clip(left(in, i) * gain);
                out[i * 2 + 1] = clip(right(in, i) * gain);
            }
            sink.frames(out, frames);
            return;
        }

        grow((int) ((frames + 2) / step) + 4);
        int made = 0;
        while (true) {
            int i0 = (int) Math.floor(pos);
            if (i0 + 1 >= frames) {
                break;
            }
            double frac = pos - i0;
            float l0 = i0 < 0 ? prevL : left(in, i0);
            float r0 = i0 < 0 ? prevR : right(in, i0);
            float l1 = left(in, i0 + 1);
            float r1 = right(in, i0 + 1);
            out[made * 2] = clip((float) (l0 + (l1 - l0) * frac) * gain);
            out[made * 2 + 1] = clip((float) (r0 + (r1 - r0) * frac) * gain);
            made++;
            pos += step;
        }
        pos -= frames;
        prevL = left(in, frames - 1);
        prevR = right(in, frames - 1);
        if (made > 0) {
            sink.frames(out, made);
        }
    }

    private float left(short[] in, int frame) {
        return in[frame * channels];
    }

    private float right(short[] in, int frame) {
        return channels == 1 ? in[frame] : in[frame * channels + 1];
    }

    private void grow(int frames) {
        if (out.length < frames * 2) {
            out = new short[frames * 2];
        }
    }

    private static short clip(float v) {
        int r = Math.round(v);
        if (r > 32767) {
            return 32767;
        }
        if (r < -32768) {
            return -32768;
        }
        return (short) r;
    }
}
