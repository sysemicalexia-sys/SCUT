package com.scut.media;

final class FrameSelector {

    private final long intervalUs;
    private long nextTargetUs;

    FrameSelector(int fps) {
        intervalUs = 1000000L / Math.max(1, fps);
    }

    boolean accept(long outPtsUs) {
        if (outPtsUs + 1000 < nextTargetUs) {
            return false;
        }
        nextTargetUs += intervalUs;
        if (nextTargetUs + intervalUs < outPtsUs) {
            nextTargetUs = outPtsUs + intervalUs;
        }
        return true;
    }
}
