import com.scut.media.ExportSettings;
import com.scut.media.PcmConverter;
import com.scut.project.Clip;
import com.scut.project.Fmt;
import com.scut.project.History;
import com.scut.project.Project;
import com.scut.project.Timeline;

import java.util.ArrayList;
import java.util.List;

public class CoreTest {

    static int fails = 0;

    static void check(boolean ok, String what) {
        if (!ok) {
            System.out.println("FAIL " + what);
            fails++;
        }
    }

    static Clip clip(long inMs, long outMs) {
        Clip c = new Clip();
        c.id = Clip.newId();
        c.uri = "u";
        c.sourceMs = 100000;
        c.inMs = inMs;
        c.outMs = outMs;
        c.width = 1080;
        c.height = 1920;
        return c;
    }

    public static void main(String[] args) {
        List<Clip> clips = new ArrayList<Clip>();
        clips.add(clip(0, 4000));
        clips.add(clip(1000, 3000));
        clips.add(clip(500, 2500));

        check(Timeline.total(clips) == 8000, "total");
        check(Timeline.startOf(clips, 0) == 0 && Timeline.startOf(clips, 1) == 4000 && Timeline.startOf(clips, 2) == 6000, "startOf");

        Timeline.Spot s = Timeline.locate(clips, 0);
        check(s.index == 0 && s.offsetMs == 0, "locate 0");
        s = Timeline.locate(clips, 3999);
        check(s.index == 0 && s.offsetMs == 3999, "locate 3999");
        s = Timeline.locate(clips, 4000);
        check(s.index == 1 && s.offsetMs == 0, "locate boundary goes to next clip");
        s = Timeline.locate(clips, 8000);
        check(s.index == 2 && s.offsetMs == 2000, "locate end");
        s = Timeline.locate(clips, 99999);
        check(s.index == 2 && s.offsetMs == 2000, "locate past end clamps");
        s = Timeline.locate(clips, -50);
        check(s.index == 0 && s.offsetMs == 0, "locate negative clamps");
        check(Timeline.locate(new ArrayList<Clip>(), 10) == null, "locate empty");

        check(!Timeline.split(clips, 0, 50, 100), "split too close to start");
        check(!Timeline.split(clips, 0, 3950, 100), "split too close to end");
        check(!Timeline.split(clips, 9, 1000, 100), "split bad index");
        List<Clip> before = Timeline.snapshot(clips);
        check(Timeline.split(clips, 1, 800, 100), "split ok");
        check(clips.size() == 4, "split adds clip");
        check(clips.get(1).inMs == 1000 && clips.get(1).outMs == 1800, "left half");
        check(clips.get(2).inMs == 1800 && clips.get(2).outMs == 3000, "right half");
        check(!clips.get(1).id.equals(clips.get(2).id), "new id for right half");
        check(Timeline.total(clips) == 8000, "split keeps total");
        check(before.get(1).outMs == 3000, "snapshot is a deep copy");

        History h = new History();
        List<Clip> live = new ArrayList<Clip>();
        live.add(clip(0, 1000));
        h.record(live);
        live.get(0).outMs = 500;
        check(h.canUndo() && !h.canRedo(), "history flags");
        check(h.undo(live) && live.get(0).outMs == 1000, "undo restores");
        check(h.canRedo() && h.redo(live) && live.get(0).outMs == 500, "redo restores");
        h.record(live);
        h.undo(live);
        h.record(live);
        check(!h.canRedo(), "new edit clears redo");
        for (int i = 0; i < 100; i++) {
            h.record(live);
        }
        int n = 0;
        while (h.undo(live)) {
            n++;
        }
        check(n == 60, "history limit is 60, got " + n);

        Project p = new Project();
        check(Math.abs(p.aspect() - 16f / 9f) < 0.001f, "empty project aspect");
        p.clips.add(clip(0, 1000));
        check(Math.abs(p.aspect() - 1080f / 1920f) < 0.001f, "project aspect from first clip");

        check(Fmt.clock(0).equals("00:00") && Fmt.clock(4999).equals("00:04") && Fmt.clock(65000).equals("01:05") && Fmt.clock(3723000).equals("1:02:03"), "clock");
        check(Fmt.badge(5000).equals("0:05") && Fmt.badge(125000).equals("2:05"), "badge");
        check(Fmt.length(5200).equals("5.2s") && Fmt.length(61000).equals("1:01"), "length");
        check(Fmt.size(500).equals("1 KB") || Fmt.size(500).equals("0 KB") || Fmt.size(500).endsWith("KB"), "size kb");
        check(Fmt.size(5L << 20).equals("5.0 MB") && Fmt.size(3L << 30).equals("3.0 GB"), "size mb gb");

        int[] c1 = ExportSettings.canvas(9f / 16f, 1080);
        check(c1[0] == 1080 && c1[1] == 1920, "canvas portrait 1080: " + c1[0] + "x" + c1[1]);
        int[] c2 = ExportSettings.canvas(16f / 9f, 720);
        check(c2[0] == 1280 && c2[1] == 720, "canvas landscape 720: " + c2[0] + "x" + c2[1]);
        int[] c3 = ExportSettings.canvas(1f, 480);
        check(c3[0] == 480 && c3[1] == 480, "canvas square");
        int[] c4 = ExportSettings.canvas(0.5625f * 1.001f, 721);
        check(c4[0] % 2 == 0 && c4[1] % 2 == 0, "canvas dims are even");
        int b = ExportSettings.bitrate(1080, 1920, 30, 1);
        check(b > 5000000 && b < 8000000, "bitrate recommended 1080p30 = " + b);
        check(ExportSettings.bitrate(1080, 1920, 30, 0) < b && ExportSettings.bitrate(1080, 1920, 30, 2) > b, "quality ordering");
        check(ExportSettings.bitrate(2, 2, 1, 0) == 400000, "bitrate floor");
        check(ExportSettings.estimateBytes(10000, 8000000, true) == (8000000 + 128000) / 8 * 10, "estimate");
        check(ExportSettings.sideLabel(1080).equals("1080P") && ExportSettings.sideLabel(1440).equals("2K") && ExportSettings.sideLabel(2160).equals("4K"), "labels");

        final int[] total = {0};
        PcmConverter.Sink count = new PcmConverter.Sink() {
            public void frames(short[] stereo, int n) {
                total[0] += n;
            }
        };

        short[] mono = new short[1000];
        for (int i = 0; i < mono.length; i++) {
            mono[i] = (short) (i - 500);
        }
        final short[][] grab = new short[1][];
        PcmConverter same = new PcmConverter(48000, 1, 48000, 1f);
        same.convert(mono, 1000, new PcmConverter.Sink() {
            public void frames(short[] stereo, int n) {
                grab[0] = java.util.Arrays.copyOf(stereo, n * 2);
            }
        });
        boolean exact = grab[0].length == 2000;
        for (int i = 0; exact && i < 1000; i++) {
            exact = grab[0][i * 2] == mono[i] && grab[0][i * 2 + 1] == mono[i];
        }
        check(exact, "mono to stereo passthrough");

        PcmConverter loud = new PcmConverter(44100, 2, 44100, 2f);
        short[] st = {20000, -20000, 100, -100};
        loud.convert(st, 2, new PcmConverter.Sink() {
            public void frames(short[] stereo, int n) {
                grab[0] = java.util.Arrays.copyOf(stereo, n * 2);
            }
        });
        check(grab[0][0] == 32767 && grab[0][1] == -32768 && grab[0][2] == 200 && grab[0][3] == -200, "gain with clipping");

        PcmConverter six = new PcmConverter(48000, 6, 48000, 1f);
        short[] surround = {10, 20, 30, 40, 50, 60, 11, 21, 31, 41, 51, 61};
        six.convert(surround, 2, new PcmConverter.Sink() {
            public void frames(short[] stereo, int n) {
                grab[0] = java.util.Arrays.copyOf(stereo, n * 2);
            }
        });
        check(grab[0][0] == 10 && grab[0][1] == 20 && grab[0][2] == 11 && grab[0][3] == 21, "surround takes front l/r");

        PcmConverter down = new PcmConverter(48000, 2, 44100, 1f);
        total[0] = 0;
        int srcFrames = 0;
        double phase = 0;
        short[] block = new short[2 * 1024];
        double maxJump = 0;
        final double[] lastL = {Double.NaN};
        final double[] jump = {0};
        PcmConverter.Sink track = new PcmConverter.Sink() {
            public void frames(short[] stereo, int n) {
                total[0] += n;
                for (int i = 0; i < n; i++) {
                    double v = stereo[i * 2];
                    if (!Double.isNaN(lastL[0])) {
                        jump[0] = Math.max(jump[0], Math.abs(v - lastL[0]));
                    }
                    lastL[0] = v;
                }
            }
        };
        for (int b2 = 0; b2 < 100; b2++) {
            for (int i = 0; i < 1024; i++) {
                short v = (short) (Math.sin(phase) * 10000);
                block[i * 2] = v;
                block[i * 2 + 1] = v;
                phase += 2 * Math.PI * 440 / 48000;
            }
            down.convert(block, 1024, track);
            srcFrames += 1024;
        }
        double expected = srcFrames * 44100.0 / 48000.0;
        check(Math.abs(total[0] - expected) < 3, "48k to 44.1k length " + total[0] + " vs " + expected);
        double maxStep = 2 * Math.PI * 440 / 44100 * 10000 * 1.05;
        check(jump[0] <= maxStep, "no discontinuity across blocks, biggest jump " + jump[0] + " allowed " + maxStep);

        PcmConverter up = new PcmConverter(22050, 1, 44100, 1f);
        total[0] = 0;
        short[] ramp = new short[500];
        up.convert(ramp, 500, count);
        up.convert(ramp, 500, count);
        check(Math.abs(total[0] - 2000) < 4, "22k to 44k doubles the frames: " + total[0]);

        System.out.println(fails == 0 ? "core ok" : fails + " failures");
        System.exit(fails == 0 ? 0 : 1);
    }
}
