package com.scut.ui.export;

import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.scut.engine.MainActivity;
import com.scut.media.Capabilities;
import com.scut.media.ExportSettings;
import com.scut.media.Exporter;
import com.scut.media.MediaLibrary;
import com.scut.media.Thumbs;
import com.scut.project.Clip;
import com.scut.project.Fmt;
import com.scut.project.Project;
import com.scut.ui.BaseScreen;
import com.scut.ui.Services;
import com.scut.ui.theme.ScutTheme;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ExportScreen extends BaseScreen {

    private static final int RUNNING = 0;
    private static final int SAVING = 1;
    private static final int DONE = 2;
    private static final int FAILED = 3;

    private final MainActivity host;
    private final Services sv;
    private final Project project;
    private final FrameLayout root;
    private final Poster poster;
    private final Bar bar;
    private final TextView title;
    private final TextView subtitle;
    private final TextView percent;
    private final LinearLayout buttons;

    private Exporter exporter;
    private Uri saved;
    private int state = RUNNING;
    private boolean started;

    public ExportScreen(MainActivity host, Project project) {
        this.host = host;
        this.sv = Services.get(host);
        this.project = project;

        root = new FrameLayout(host);
        root.setBackgroundColor(ScutTheme.BG);

        LinearLayout column = new LinearLayout(host);
        column.setOrientation(LinearLayout.VERTICAL);
        int side = ScutTheme.dp(host, 24);
        column.setPadding(side, ScutTheme.dp(host, 24), side, side);

        poster = new Poster(host);
        column.addView(poster, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        title = ScutTheme.label(host, "Exporting", 22, ScutTheme.TEXT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, ScutTheme.dp(host, 20), 0, 0);
        column.addView(title, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        subtitle = ScutTheme.label(host, "", 14, ScutTheme.TEXT_DIM, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, ScutTheme.dp(host, 6), 0, ScutTheme.dp(host, 18));
        column.addView(subtitle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        bar = new Bar(host);
        column.addView(bar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ScutTheme.dp(host, 8)));

        percent = ScutTheme.label(host, "0%", 14, ScutTheme.TEXT, true);
        percent.setGravity(Gravity.CENTER);
        percent.setPadding(0, ScutTheme.dp(host, 10), 0, ScutTheme.dp(host, 20));
        column.addView(percent, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        buttons = new LinearLayout(host);
        buttons.setOrientation(LinearLayout.VERTICAL);
        column.addView(buttons, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onShow() {
        if (!started) {
            started = true;
            poster.load();
            start();
        }
    }

    @Override
    public boolean onBack() {
        if (state == RUNNING) {
            askStop();
            return true;
        }
        return state == SAVING;
    }

    @Override
    public void onDestroy() {
        if (state == RUNNING && exporter != null) {
            exporter.cancel();
        }
        host.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void start() {
        state = RUNNING;
        title.setText("Exporting");
        subtitle.setText("Keep SCUT open and leave the screen on");
        bar.set(0f);
        percent.setText("0%");
        percent.setVisibility(View.VISIBLE);
        setButtons(button("Cancel", false, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askStop();
            }
        }));
        host.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Exporter.Job job = new Exporter.Job();
        int[] canvas = ExportSettings.canvas(project.aspect(), project.exportSide);
        job.width = canvas[0];
        job.height = canvas[1];
        job.fps = project.exportFps;
        job.videoBitrate = Capabilities.clampBitrate(
                ExportSettings.bitrate(job.width, job.height, job.fps, project.exportQuality));
        job.audioRate = 44100;
        for (Clip c : project.clips) {
            if (c.hasAudio && c.sampleRate > 0) {
                job.audioRate = Math.max(8000, Math.min(c.sampleRate, 48000));
                break;
            }
        }
        for (Clip c : project.clips) {
            Exporter.Segment s = new Exporter.Segment();
            s.uri = Uri.parse(c.uri);
            s.name = c.name;
            s.inUs = c.inMs * 1000L;
            s.outUs = c.outMs * 1000L;
            s.volume = c.volume;
            s.hasAudio = c.hasAudio;
            s.dispW = c.width;
            s.dispH = c.height;
            job.segments.add(s);
        }
        job.output = new File(new File(host.getCacheDir(), "export"), "scut_" + System.currentTimeMillis() + ".mp4");

        exporter = new Exporter(host, job, new Exporter.Callback() {
            @Override
            public void onProgress(float fraction) {
                if (state != RUNNING) {
                    return;
                }
                bar.set(fraction);
                percent.setText(Math.round(fraction * 100) + "%");
            }

            @Override
            public void onDone(File output) {
                save(output);
            }

            @Override
            public void onFailed(String message) {
                fail(message);
            }

            @Override
            public void onCancelled() {
                host.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                host.pop();
            }
        });
        exporter.start();
    }

    private void save(final File output) {
        state = SAVING;
        title.setText("Saving");
        subtitle.setText("Putting it in your gallery");
        bar.set(1f);
        percent.setVisibility(View.INVISIBLE);
        setButtons();
        final String name = "SCUT_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".mp4";
        final long bytes = output.length();
        sv.io.execute(new Runnable() {
            @Override
            public void run() {
                Uri uri = null;
                String error = null;
                try {
                    uri = MediaLibrary.saveToGallery(sv.app, output, name);
                } catch (IOException e) {
                    error = e.getMessage() == null ? "couldn't save the file" : e.getMessage();
                } catch (RuntimeException e) {
                    error = "couldn't save the file";
                }
                output.delete();
                final Uri result = uri;
                final String problem = error;
                sv.main.post(new Runnable() {
                    @Override
                    public void run() {
                        if (result == null) {
                            fail(problem);
                        } else {
                            finished(result, bytes);
                        }
                    }
                });
            }
        });
    }

    private void finished(Uri uri, long bytes) {
        saved = uri;
        state = DONE;
        host.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        int[] canvas = ExportSettings.canvas(project.aspect(), project.exportSide);
        title.setText("Saved to gallery");
        subtitle.setText(canvas[0] + " x " + canvas[1] + "  \u00b7  " + Fmt.size(bytes) + "  \u00b7  Movies/SCUT");
        percent.setVisibility(View.GONE);
        setButtons(
                button("Share", true, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        share();
                    }
                }),
                button("Open in gallery", false, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        open();
                    }
                }),
                button("Done", false, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        host.pop();
                    }
                }));
    }

    private void fail(String message) {
        state = FAILED;
        host.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        title.setText("Export failed");
        subtitle.setText(message == null ? "something went wrong" : message);
        percent.setVisibility(View.GONE);
        setButtons(
                button("Try again", true, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        start();
                    }
                }),
                button("Close", false, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        host.pop();
                    }
                }));
    }

    private void askStop() {
        new AlertDialog.Builder(host)
                .setTitle("Stop exporting?")
                .setMessage("The video won't be saved.")
                .setNegativeButton("Keep going", null)
                .setPositiveButton("Stop", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (state == RUNNING && exporter != null) {
                            exporter.cancel();
                        }
                    }
                })
                .show();
    }

    private void share() {
        if (saved == null) {
            return;
        }
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("video/mp4");
        i.putExtra(Intent.EXTRA_STREAM, saved);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        host.startActivity(Intent.createChooser(i, "Share video"));
    }

    private void open() {
        if (saved == null) {
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(saved, "video/mp4");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            host.startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(host, "no app to play it", Toast.LENGTH_SHORT).show();
        }
    }

    private View button(String text, boolean primary, View.OnClickListener click) {
        TextView t = ScutTheme.pill(host, text, primary ? ScutTheme.ACCENT : ScutTheme.SURFACE,
                primary ? ScutTheme.ON_ACCENT : ScutTheme.TEXT);
        t.setBackground(ScutTheme.box(primary ? ScutTheme.ACCENT : ScutTheme.SURFACE, 0, 0, ScutTheme.dp(host, 24)));
        t.setOnClickListener(click);
        return t;
    }

    private void setButtons(View... views) {
        buttons.removeAllViews();
        for (View v : views) {
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ScutTheme.dp(host, 48));
            p.topMargin = ScutTheme.dp(host, 10);
            buttons.addView(v, p);
        }
    }

    private final class Bar extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private float value;

        Bar(android.content.Context c) {
            super(c);
        }

        void set(float v) {
            value = Math.max(0f, Math.min(1f, v));
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            float r = getHeight() / 2f;
            rect.set(0, 0, getWidth(), getHeight());
            paint.setColor(ScutTheme.SURFACE2);
            c.drawRoundRect(rect, r, r, paint);
            if (value > 0f) {
                rect.set(0, 0, Math.max(getHeight(), getWidth() * value), getHeight());
                paint.setColor(ScutTheme.ACCENT);
                c.drawRoundRect(rect, r, r, paint);
            }
        }
    }

    private final class Poster extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final String key;

        private final int pw = 480;
        private final int ph;

        Poster(android.content.Context c) {
            super(c);
            ph = Math.max(1, Math.round(pw / project.aspect()));
            key = "x:" + project.id + ":" + pw + "x" + ph;
        }

        void load() {
            if (project.clips.isEmpty() || sv.thumbs.peek(key) != null) {
                return;
            }
            Clip first = project.clips.get(0);
            sv.thumbs.poster(key, first.uri, first.inMs * 1000L, pw, ph, new Runnable() {
                @Override
                public void run() {
                    invalidate();
                }
            });
        }

        @Override
        protected void onDraw(Canvas c) {
            Bitmap b = sv.thumbs.peek(key);
            float w = getWidth();
            float h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            float aspect = project.aspect();
            float rw;
            float rh;
            if (w / h > aspect) {
                rh = h;
                rw = rh * aspect;
            } else {
                rw = w;
                rh = rw / aspect;
            }
            rect.set((w - rw) / 2f, (h - rh) / 2f, (w + rw) / 2f, (h + rh) / 2f);
            paint.setColor(ScutTheme.CANVAS);
            float rad = ScutTheme.dp(getContext(), 14);
            c.drawRoundRect(rect, rad, rad, paint);
            if (b != null) {
                android.graphics.Path clip = new android.graphics.Path();
                clip.addRoundRect(rect, rad, rad, android.graphics.Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                c.drawBitmap(b, null, rect, paint);
                c.restore();
            }
        }
    }
}
