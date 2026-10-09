package com.scut.media;

public class SelectorTest {

    static int fails = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            System.out.println("FAIL " + what);
            fails++;
        }
    }

    static int[] run(double srcFps, int outFps, double seconds) {
        FrameSelector sel = new FrameSelector(outFps);
        int kept = 0;
        long last = -1;
        long maxGap = 0;
        int frames = (int) (seconds * srcFps);
        for (int i = 0; i < frames; i++) {
            long pts = Math.round(i * 1000000.0 / srcFps);
            if (sel.accept(pts)) {
                kept++;
                if (last >= 0) {
                    maxGap = Math.max(maxGap, pts - last);
                }
                last = pts;
            }
        }
        return new int[] {kept, (int) (maxGap / 1000)};
    }

    public static void main(String[] args) {
        int[] r = run(30, 30, 10);
        check(r[0] == 300, "30 to 30 keeps everything, got " + r[0]);

        r = run(29.97, 30, 10);
        check(r[0] == (int) (10 * 29.97), "29.97 to 30 keeps everything, got " + r[0]);

        r = run(60, 30, 10);
        check(Math.abs(r[0] - 300) <= 2 && r[1] <= 34, "60 to 30 about 300, got " + r[0] + " maxgap " + r[1]);

        r = run(30, 25, 10);
        check(Math.abs(r[0] - 250) <= 3 && r[1] <= 70, "30 to 25 about 250, got " + r[0] + " maxgap " + r[1]);

        r = run(24, 60, 10);
        check(r[0] == 240, "24 to 60 keeps everything, got " + r[0]);

        r = run(120, 24, 10);
        check(Math.abs(r[0] - 240) <= 2, "120 to 24 about 240, got " + r[0]);

        r = run(30, 24, 10);
        check(Math.abs(r[0] - 240) <= 3 && r[1] <= 75, "30 to 24 about 240, got " + r[0] + " maxgap " + r[1]);

        FrameSelector sel = new FrameSelector(30);
        check(sel.accept(0), "first frame always");
        check(sel.accept(500000), "late frame after a gap accepted");
        check(!sel.accept(500100), "and the next close one dropped");
        check(sel.accept(540000), "resynced after the gap");

        System.out.println(fails == 0 ? "selector ok" : fails + " failures");
        System.exit(fails == 0 ? 0 : 1);
    }
}
