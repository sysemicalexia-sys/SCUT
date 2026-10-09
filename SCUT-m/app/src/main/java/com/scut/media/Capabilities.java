package com.scut.media;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;

public final class Capabilities {

    private Capabilities() {
    }

    public static boolean canEncode(int w, int h) {
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (!info.isEncoder()) {
                    continue;
                }
                for (String type : info.getSupportedTypes()) {
                    if (!type.equalsIgnoreCase("video/avc")) {
                        continue;
                    }
                    MediaCodecInfo.VideoCapabilities caps =
                            info.getCapabilitiesForType(type).getVideoCapabilities();
                    if (caps != null && caps.isSizeSupported(w, h)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (RuntimeException e) {
            return true;
        }
    }

    public static int clampBitrate(int bitrate) {
        try {
            MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            for (MediaCodecInfo info : list.getCodecInfos()) {
                if (!info.isEncoder()) {
                    continue;
                }
                for (String type : info.getSupportedTypes()) {
                    if (!type.equalsIgnoreCase("video/avc")) {
                        continue;
                    }
                    MediaCodecInfo.VideoCapabilities caps =
                            info.getCapabilitiesForType(type).getVideoCapabilities();
                    if (caps != null) {
                        return caps.getBitrateRange().clamp(bitrate);
                    }
                }
            }
        } catch (RuntimeException ignored) {
        }
        return bitrate;
    }
}
