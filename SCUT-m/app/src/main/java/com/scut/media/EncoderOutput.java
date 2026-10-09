package com.scut.media;

import android.media.MediaCodec;

import java.io.IOException;
import java.nio.ByteBuffer;

final class EncoderOutput {

    private final MediaCodec encoder;
    private final MuxerGate gate;
    private final Exporter.Control ctl;
    private final MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    private int track = -1;

    EncoderOutput(MediaCodec encoder, MuxerGate gate, Exporter.Control ctl) {
        this.encoder = encoder;
        this.gate = gate;
        this.ctl = ctl;
    }

    void drain(boolean endOfStream) throws Exception {
        long deadline = System.currentTimeMillis() + 20000;
        while (true) {
            ctl.check();
            int oi = encoder.dequeueOutputBuffer(info, endOfStream ? 10000 : 0);
            if (oi == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) {
                    return;
                }
                if (System.currentTimeMillis() > deadline) {
                    throw new IOException("encoder never finished");
                }
                continue;
            }
            if (oi == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                track = gate.addTrack(encoder.getOutputFormat());
                continue;
            }
            if (oi < 0) {
                continue;
            }
            ByteBuffer data = encoder.getOutputBuffer(oi);
            if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                info.size = 0;
            }
            if (info.size > 0 && data != null) {
                if (track < 0) {
                    throw new IOException("encoder sent data before its format");
                }
                data.position(info.offset);
                data.limit(info.offset + info.size);
                gate.write(track, data, info);
            }
            encoder.releaseOutputBuffer(oi, false);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                return;
            }
        }
    }
}
