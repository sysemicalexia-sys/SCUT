package com.scut.ui.editor;

import android.view.Gravity;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.scut.engine.MainActivity;
import com.scut.media.Capabilities;
import com.scut.media.ExportSettings;
import com.scut.player.PreviewPlayer;
import com.scut.project.Clip;
import com.scut.project.Fmt;
import com.scut.project.History;
import com.scut.project.Project;
import com.scut.project.Timeline;
import com.scut.ui.BaseScreen;
import com.scut.ui.Services;
import com.scut.ui.export.ExportScreen;
import com.scut.ui.picker.PickerScreen;
import com.scut.ui.theme.ScutTheme;
import com.scut.ui.widgets.BottomSheet;
import com.scut.ui.widgets.IconView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EditorScreen extends BaseScreen {

    private interface Pick {
        void on(int index);
    }

    private static final long MIN_CLIP_MS = 100;

    private final MainActivity host;
    private final Services sv;
    private final Project project;
    private final History history = new History();
    private final FrameLayout root;
    private final PreviewBox previewBox;
    private final PreviewPlayer player;
    private final TimelineView timeline;
    private final TextView timeNow;
    private final TextView timeTotal;
    private final TextView resText;
    private final IconView playIcon;
    private final IconView undoIcon;
    private final IconView redoIcon;
    private final FrameLayout toolbarHost;
    private final BottomSheet sheet;
    private final Runnable saveLater = new Runnable() {
        @Override
        public void run() {
            sv.saveAsync(project);
        }
    };

    private int selected = -1;
    private long timeMs;

    public EditorScreen(MainActivity host, Project project) {
        this.host = host;
        this.sv = Services.get(host);
        this.project = project;

        root = new FrameLayout(host);
        root.setBackgroundColor(ScutTheme.BG);

        LinearLayout column = new LinearLayout(host);
        column.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(host);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(0, 0, ScutTheme.dp(host, 12), 0);
        IconView close = new IconView(host, IconView.CLOSE, ScutTheme.TEXT);
        close.setPadding(ScutTheme.dp(host, 14), ScutTheme.dp(host, 14), ScutTheme.dp(host, 14), ScutTheme.dp(host, 14));
        ScutTheme.pressAlpha(close);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                leave();
            }
        });
        top.addView(close, new LinearLayout.LayoutParams(ScutTheme.dp(host, 52), ScutTheme.dp(host, 52)));
        top.addView(new View(host), new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout chip = new LinearLayout(host);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        chip.setPadding(ScutTheme.dp(host, 12), 0, ScutTheme.dp(host, 8), 0);
        chip.setBackground(ScutTheme.box(ScutTheme.SURFACE, 0, 0, ScutTheme.dp(host, 18)));
        resText = ScutTheme.label(host, ExportSettings.sideLabel(project.exportSide), 14, ScutTheme.TEXT, true);
        chip.addView(resText);
        chip.addView(new IconView(host, IconView.CHEVRON_DOWN, ScutTheme.TEXT),
                new LinearLayout.LayoutParams(ScutTheme.dp(host, 18), ScutTheme.dp(host, 18)));
        ScutTheme.pressAlpha(chip);
        chip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                exportSheet();
            }
        });
        top.addView(chip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ScutTheme.dp(host, 36)));

        TextView export = ScutTheme.pill(host, "Export", ScutTheme.ACCENT, ScutTheme.ON_ACCENT);
        export.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                export();
            }
        });
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(ScutTheme.dp(host, 88), ScutTheme.dp(host, 36));
        ep.leftMargin = ScutTheme.dp(host, 10);
        top.addView(export, ep);
        column.addView(top, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ScutTheme.dp(host, 52)));

        TextureView texture = new TextureView(host);
        previewBox = new PreviewBox(host, texture);
        previewBox.setBackgroundColor(ScutTheme.SURFACE);
        column.addView(previewBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout controls = new LinearLayout(host);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER_VERTICAL);
        controls.setBackgroundColor(ScutTheme.BG);
        controls.setPadding(ScutTheme.dp(host, 16), 0, ScutTheme.dp(host, 8), 0);

        LinearLayout times = new LinearLayout(host);
        times.setOrientation(LinearLayout.HORIZONTAL);
        timeNow = ScutTheme.label(host, "00:00", 13, ScutTheme.TEXT, true);
        timeTotal = ScutTheme.label(host, " / 00:00", 13, ScutTheme.TEXT_DIM, false);
        times.addView(timeNow);
        times.addView(timeTotal);
        controls.addView(times, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        playIcon = new IconView(host, IconView.PLAY, ScutTheme.TEXT);
        playIcon.setPadding(ScutTheme.dp(host, 10), ScutTheme.dp(host, 10), ScutTheme.dp(host, 10), ScutTheme.dp(host, 10));
        ScutTheme.pressAlpha(playIcon);
        playIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlay();
            }
        });
        controls.addView(playIcon, new LinearLayout.LayoutParams(ScutTheme.dp(host, 48), ScutTheme.dp(host, 48)));

        LinearLayout history2 = new LinearLayout(host);
        history2.setOrientation(LinearLayout.HORIZONTAL);
        history2.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        undoIcon = new IconView(host, IconView.UNDO, ScutTheme.TEXT);
        redoIcon = new IconView(host, IconView.REDO, ScutTheme.TEXT);
        undoIcon.setPadding(ScutTheme.dp(host, 10), ScutTheme.dp(host, 10), ScutTheme.dp(host, 10), ScutTheme.dp(host, 10));
        redoIcon.setPadding(ScutTheme.dp(host, 10), ScutTheme.dp(host, 10), ScutTheme.dp(host, 10), ScutTheme.dp(host, 10));
        ScutTheme.pressAlpha(undoIcon);
        ScutTheme.pressAlpha(redoIcon);
        undoIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                undo();
            }
        });
        redoIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                redo();
            }
        });
        history2.addView(undoIcon, new LinearLayout.LayoutParams(ScutTheme.dp(host, 44), ScutTheme.dp(host, 44)));
        history2.addView(redoIcon, new LinearLayout.LayoutParams(ScutTheme.dp(host, 44), ScutTheme.dp(host, 44)));
        controls.addView(history2, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        column.addView(controls, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ScutTheme.dp(host, 48)));

        FrameLayout timelinePanel = new FrameLayout(host);
        timelinePanel.setBackgroundColor(ScutTheme.SURFACE);
        timeline = new TimelineView(host, sv.thumbs, new TimelineView.Listener() {
            @Override
            public void onScrubStart() {
                if (player.isPlaying()) {
                    player.pause();
                }
            }

            @Override
            public void onScrub(long ms) {
                timeMs = ms;
                player.seekTo(ms);
                updateTime();
            }

            @Override
            public void onScrubEnd() {
            }

            @Override
            public void onClipTap(int index) {
                select(index);
            }

            @Override
            public void onAdd() {
                addMore();
            }
        });
        timelinePanel.addView(timeline, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        View playhead = new View(host);
        playhead.setBackground(ScutTheme.box(ScutTheme.TEXT, 0, 0, ScutTheme.dp(host, 1)));
        timelinePanel.addView(playhead, new FrameLayout.LayoutParams(
                ScutTheme.dp(host, 2), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.CENTER_HORIZONTAL));
        column.addView(timelinePanel, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ScutTheme.dp(host, TimelineView.heightDp())));

        View line = new View(host);
        line.setBackgroundColor(ScutTheme.DIVIDER);
        column.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        toolbarHost = new FrameLayout(host);
        toolbarHost.setBackgroundColor(ScutTheme.BG);
        column.addView(toolbarHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ScutTheme.dp(host, 68)));

        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sheet = new BottomSheet(host);
        root.addView(sheet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        player = new PreviewPlayer(host, texture, new PreviewPlayer.Listener() {
            @Override
            public void onTime(long ms) {
                timeMs = ms;
                timeline.setPlayhead(ms);
                updateTime();
            }

            @Override
            public void onState(boolean playing) {
                playIcon.setIcon(playing ? IconView.PAUSE : IconView.PLAY);
            }

            @Override
            public void onClip(Clip clip) {
                previewBox.setAspects(EditorScreen.this.project.aspect(), clip.width / (float) clip.height);
            }

            @Override
            public void onError(String message) {
                toast(message);
            }
        });

        bind();
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onShow() {
        playIcon.setIcon(IconView.PLAY);
        player.setClips(project.clips, timeMs);
    }

    @Override
    public void onHide() {
        player.release();
        playIcon.setIcon(IconView.PLAY);
        sv.main.removeCallbacks(saveLater);
        sv.saveAsync(project);
    }

    @Override
    public boolean onBack() {
        if (sheet.isShowing()) {
            sheet.hide();
            return true;
        }
        if (selected >= 0) {
            select(-1);
            return true;
        }
        return false;
    }

    @Override
    public void onDestroy() {
        player.release();
        sv.main.removeCallbacks(saveLater);
        sv.saveAsync(project);
        sv.refreshPoster(project);
    }

    private void leave() {
        host.pop();
    }

    private void toast(String m) {
        Toast.makeText(host, m, Toast.LENGTH_SHORT).show();
    }

    private void bind() {
        float clipAspect = clipAspectAt(timeMs);
        previewBox.setAspects(project.aspect(), clipAspect);
        timeline.setClips(project.clips, selected, timeMs);
        updateTime();
        updateHistory();
        showTools();
    }

    private float clipAspectAt(long ms) {
        Timeline.Spot s = Timeline.locate(project.clips, ms);
        if (s == null) {
            return project.aspect();
        }
        Clip c = project.clips.get(s.index);
        return c.width / (float) c.height;
    }

    private void afterEdit() {
        project.modified = System.currentTimeMillis();
        long total = project.totalMs();
        if (timeMs > total) {
            timeMs = total;
        }
        if (selected >= project.clips.size()) {
            selected = -1;
        }
        bind();
        player.setClips(project.clips, timeMs);
        saveSoon();
    }

    private void saveSoon() {
        sv.main.removeCallbacks(saveLater);
        sv.main.postDelayed(saveLater, 600);
    }

    private void updateTime() {
        timeNow.setText(Fmt.clock(timeMs));
        timeTotal.setText(" / " + Fmt.clock(project.totalMs()));
    }

    private void updateHistory() {
        undoIcon.setAlpha(history.canUndo() ? 1f : 0.3f);
        redoIcon.setAlpha(history.canRedo() ? 1f : 0.3f);
    }

    private void select(int index) {
        selected = index;
        timeline.setSelected(index);
        showTools();
    }

    private void togglePlay() {
        if (project.clips.isEmpty()) {
            return;
        }
        if (player.isPlaying()) {
            player.pause();
        } else {
            player.play();
        }
    }

    private void undo() {
        if (history.undo(project.clips)) {
            selected = -1;
            afterEdit();
        }
    }

    private void redo() {
        if (history.redo(project.clips)) {
            selected = -1;
            afterEdit();
        }
    }

    private Clip target() {
        if (selected >= 0 && selected < project.clips.size()) {
            return project.clips.get(selected);
        }
        Timeline.Spot s = Timeline.locate(project.clips, timeMs);
        return s == null ? null : project.clips.get(s.index);
    }

    private void split() {
        Timeline.Spot s = Timeline.locate(project.clips, timeMs);
        if (s == null) {
            return;
        }
        List<Clip> test = Timeline.snapshot(project.clips);
        if (!Timeline.split(test, s.index, s.offsetMs, MIN_CLIP_MS)) {
            toast("move the playhead inside a clip first");
            return;
        }
        history.record(project.clips);
        Timeline.restore(project.clips, test);
        selected = s.index;
        afterEdit();
    }

    private void deleteSelected() {
        if (selected < 0 || selected >= project.clips.size()) {
            toast("tap a clip first");
            return;
        }
        history.record(project.clips);
        project.clips.remove(selected);
        selected = -1;
        afterEdit();
    }

    private void addMore() {
        player.pause();
        host.push(new PickerScreen(host, new PickerScreen.Callback() {
            @Override
            public void onPicked(List<Clip> clips) {
                host.pop();
                long oldTotal = project.totalMs();
                history.record(project.clips);
                project.clips.addAll(clips);
                selected = -1;
                timeMs = oldTotal;
                afterEdit();
            }
        }));
    }

    private void export() {
        if (project.clips.isEmpty()) {
            toast("add a video first");
            return;
        }
        player.pause();
        host.push(new ExportScreen(host, project));
    }

    private View tool(int icon, String label, boolean ready, View.OnClickListener click) {
        LinearLayout t = new LinearLayout(host);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.addView(new IconView(host, icon, ScutTheme.TEXT),
                new LinearLayout.LayoutParams(ScutTheme.dp(host, 24), ScutTheme.dp(host, 24)));
        TextView l = ScutTheme.label(host, label, 11, ScutTheme.TEXT, false);
        l.setPadding(0, ScutTheme.dp(host, 4), 0, 0);
        t.addView(l);
        if (ready) {
            ScutTheme.pressAlpha(t);
        } else {
            t.setAlpha(0.4f);
        }
        t.setOnClickListener(click);
        return t;
    }

    private View soon(int icon, final String label) {
        return tool(icon, label, false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toast(label + " is coming later");
            }
        });
    }

    private void showTools() {
        toolbarHost.removeAllViews();
        HorizontalScrollView bar = new HorizontalScrollView(host);
        bar.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(ScutTheme.dp(host, 8), 0, ScutTheme.dp(host, 8), 0);
        int w = ScutTheme.dp(host, 76);

        if (selected >= 0 && selected < project.clips.size()) {
            row.addView(tool(IconView.BACK, "Back", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(-1);
                }
            }), new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
            row.addView(tool(IconView.SPLIT, "Split", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    split();
                }
            }), new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
            row.addView(tool(IconView.VOLUME, "Volume", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    volumeSheet();
                }
            }), new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
            row.addView(tool(IconView.TRASH, "Delete", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    deleteSelected();
                }
            }), new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
            row.addView(tool(IconView.INFO, "Details", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    infoSheet();
                }
            }), new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            row.addView(tool(IconView.SCISSORS, "Edit", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Timeline.Spot s = Timeline.locate(project.clips, timeMs);
                    if (s == null) {
                        toast("add a video first");
                    } else {
                        select(s.index);
                    }
                }
            }), new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
            row.addView(tool(IconView.MUSIC, "Audio", true, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    volumeSheet();
                }
            }), new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
            row.addView(soon(IconView.TEXT, "Text"), new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
            row.addView(soon(IconView.SPARKLE, "Effects"), new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
            row.addView(soon(IconView.FILTERS, "Filters"), new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
            row.addView(soon(IconView.CANVAS, "Canvas"), new LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        bar.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        toolbarHost.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void volumeSheet() {
        final Clip c = target();
        if (c == null) {
            toast("add a video first");
            return;
        }
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        final TextView value = ScutTheme.label(host, "Volume " + Math.round(c.volume * 100) + "%", 14,
                ScutTheme.TEXT_DIM, false);
        box.addView(value);
        if (!c.hasAudio) {
            TextView none = ScutTheme.label(host, "This clip has no sound", 14, ScutTheme.TEXT_DIM, false);
            none.setPadding(0, ScutTheme.dp(host, 8), 0, 0);
            box.addView(none);
        }
        SeekBar bar = new SeekBar(host);
        bar.setMax(100);
        bar.setProgress(Math.round(c.volume * 100));
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ScutTheme.ACCENT));
        bar.setThumbTintList(android.content.res.ColorStateList.valueOf(ScutTheme.ACCENT));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private boolean recorded;

            @Override
            public void onProgressChanged(SeekBar s, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                if (!recorded) {
                    recorded = true;
                    history.record(project.clips);
                }
                c.volume = progress / 100f;
                value.setText("Volume " + progress + "%");
                player.refresh();
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
                updateHistory();
                saveSoon();
            }
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ScutTheme.dp(host, 48));
        bp.topMargin = ScutTheme.dp(host, 8);
        box.addView(bar, bp);
        sheet.show("Volume", box);
    }

    private void infoSheet() {
        Clip c = target();
        if (c == null) {
            return;
        }
        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        String length = Fmt.length(c.lengthMs());
        if (c.lengthMs() != c.sourceMs) {
            length += " of " + Fmt.length(c.sourceMs);
        }
        box.addView(infoRow("Name", c.name, false));
        box.addView(infoRow("Length", length, false));
        box.addView(infoRow("Size", c.width + " x " + c.height, false));
        box.addView(infoRow("Frame rate", Math.round(c.fps) + " fps", false));
        box.addView(infoRow("Video", shortMime(c.videoMime), false));
        box.addView(infoRow("Audio", c.hasAudio
                ? shortMime(c.audioMime) + (c.sampleRate > 0 ? ", " + c.sampleRate + " Hz" : "")
                : "none", false));
        box.addView(infoRow("Bitrate", c.bitrate > 0 ? Fmt.rate(c.bitrate) : "unknown", false));
        box.addView(infoRow("File size", c.sizeBytes > 0 ? Fmt.size(c.sizeBytes) : "unknown", false));
        box.addView(infoRow("Source", c.uri, true));
        sheet.show("Details", box);
    }

    private static String shortMime(String mime) {
        if (mime == null) {
            return "unknown";
        }
        int slash = mime.indexOf('/');
        return slash >= 0 ? mime.substring(slash + 1) : mime;
    }

    private View infoRow(String key, String value, boolean selectable) {
        LinearLayout r = new LinearLayout(host);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, ScutTheme.dp(host, 6), 0, ScutTheme.dp(host, 6));
        TextView k = ScutTheme.label(host, key, 13, ScutTheme.TEXT_DIM, false);
        r.addView(k, new LinearLayout.LayoutParams(ScutTheme.dp(host, 96), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView v = ScutTheme.label(host, value, 14, ScutTheme.TEXT, false);
        v.setTextIsSelectable(selectable);
        r.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return r;
    }

    private void exportSheet() {
        final float aspect = project.aspect();
        final List<Integer> sides = new ArrayList<Integer>();
        for (int s : ExportSettings.SIDES) {
            int[] c = ExportSettings.canvas(aspect, s);
            if (Capabilities.canEncode(c[0], c[1])) {
                sides.add(s);
            }
        }
        if (sides.isEmpty()) {
            sides.add(720);
        }
        if (!sides.contains(project.exportSide)) {
            int pick = sides.get(0);
            for (int s : sides) {
                if (s <= 1080) {
                    pick = s;
                }
            }
            project.exportSide = pick;
        }

        LinearLayout box = new LinearLayout(host);
        box.setOrientation(LinearLayout.VERTICAL);
        final TextView estimate = ScutTheme.label(host, "", 13, ScutTheme.TEXT_DIM, false);
        final Runnable refresh = new Runnable() {
            @Override
            public void run() {
                int[] c = ExportSettings.canvas(aspect, project.exportSide);
                int br = ExportSettings.bitrate(c[0], c[1], project.exportFps, project.exportQuality);
                long bytes = ExportSettings.estimateBytes(project.totalMs(), br, project.hasAudio());
                estimate.setText(c[0] + " x " + c[1] + ", about " + Fmt.size(bytes));
                resText.setText(ExportSettings.sideLabel(project.exportSide));
                saveSoon();
            }
        };

        String[] sideLabels = new String[sides.size()];
        int sideAt = 0;
        for (int i = 0; i < sides.size(); i++) {
            sideLabels[i] = ExportSettings.sideLabel(sides.get(i));
            if (sides.get(i) == project.exportSide) {
                sideAt = i;
            }
        }
        box.addView(caption("Resolution"));
        box.addView(chips(sideLabels, sideAt, new Pick() {
            @Override
            public void on(int index) {
                project.exportSide = sides.get(index);
                refresh.run();
            }
        }));

        String[] fpsLabels = new String[ExportSettings.FPS.length];
        int fpsAt = 2;
        for (int i = 0; i < fpsLabels.length; i++) {
            fpsLabels[i] = String.valueOf(ExportSettings.FPS[i]);
            if (ExportSettings.FPS[i] == project.exportFps) {
                fpsAt = i;
            }
        }
        box.addView(caption("Frame rate"));
        box.addView(chips(fpsLabels, fpsAt, new Pick() {
            @Override
            public void on(int index) {
                project.exportFps = ExportSettings.FPS[index];
                refresh.run();
            }
        }));

        box.addView(caption("Quality"));
        box.addView(chips(ExportSettings.QUALITY, project.exportQuality, new Pick() {
            @Override
            public void on(int index) {
                project.exportQuality = index;
                refresh.run();
            }
        }));

        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ep.topMargin = ScutTheme.dp(host, 18);
        box.addView(estimate, ep);
        refresh.run();
        sheet.show("Export settings", box);
    }

    private View caption(String text) {
        TextView t = ScutTheme.label(host, text.toUpperCase(Locale.US), 12, ScutTheme.TEXT_DIM, true);
        t.setPadding(0, ScutTheme.dp(host, 14), 0, ScutTheme.dp(host, 8));
        return t;
    }

    private View chips(String[] labels, int selectedAt, final Pick pick) {
        HorizontalScrollView scroller = new HorizontalScrollView(host);
        scroller.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(host);
        row.setOrientation(LinearLayout.HORIZONTAL);
        final TextView[] views = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int at = i;
            TextView t = ScutTheme.label(host, labels[i], 14, ScutTheme.TEXT, true);
            t.setGravity(Gravity.CENTER);
            t.setPadding(ScutTheme.dp(host, 16), 0, ScutTheme.dp(host, 16), 0);
            t.setMinWidth(ScutTheme.dp(host, 64));
            ScutTheme.pressAlpha(t);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    styleChips(views, at);
                    pick.on(at);
                }
            });
            views[i] = t;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ScutTheme.dp(host, 38));
            p.rightMargin = ScutTheme.dp(host, 8);
            row.addView(t, p);
        }
        styleChips(views, selectedAt);
        scroller.addView(row, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroller;
    }

    private void styleChips(TextView[] views, int on) {
        for (int i = 0; i < views.length; i++) {
            boolean active = i == on;
            views[i].setTextColor(active ? ScutTheme.ACCENT : ScutTheme.TEXT);
            views[i].setBackground(ScutTheme.box(active ? 0x1A2A6BFF : ScutTheme.SURFACE,
                    ScutTheme.ACCENT, active ? ScutTheme.dp(host, 1.5f) : 0, ScutTheme.dp(host, 19)));
        }
    }
}
