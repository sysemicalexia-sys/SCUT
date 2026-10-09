package com.scut.ui.picker;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.scut.engine.MainActivity;
import com.scut.media.MediaLibrary;
import com.scut.media.MetadataReader;
import com.scut.project.Clip;
import com.scut.project.Fmt;
import com.scut.ui.BaseScreen;
import com.scut.ui.Services;
import com.scut.ui.theme.ScutTheme;
import com.scut.ui.widgets.IconView;

import java.util.ArrayList;
import java.util.List;

public final class PickerScreen extends BaseScreen {

    public interface Callback {
        void onPicked(List<Clip> clips);
    }

    private static final int REQ_PERMISSION = 31;
    private static final int REQ_FILES = 32;
    private static final int TAB_VIDEOS = 0;
    private static final int TAB_FILES = 1;

    private final MainActivity host;
    private final Services sv;
    private final Callback callback;
    private final FrameLayout root;
    private final GridView grid;
    private final LinearLayout needAccess;
    private final TextView accessButton;
    private final TextView noVideos;
    private final LinearLayout filesPanel;
    private final TextView[] tabText = new TextView[2];
    private final View[] tabLine = new View[2];
    private final TextView countText;
    private final TextView addButton;
    private final FrameLayout busy;
    private final TextView busyText;
    private final Adapter adapter = new Adapter();
    private final ArrayList<Long> picked = new ArrayList<Long>();
    private final ArrayList<Uri> extra = new ArrayList<Uri>();
    private final int cell;
    private List<MediaLibrary.Item> items = new ArrayList<MediaLibrary.Item>();
    private int tab = TAB_VIDEOS;
    private boolean importing;
    private boolean blocked;

    public PickerScreen(MainActivity host, Callback callback) {
        this.host = host;
        this.sv = Services.get(host);
        this.callback = callback;

        int gap = ScutTheme.dp(host, 2);
        cell = (host.getResources().getDisplayMetrics().widthPixels - 3 * gap) / 4;

        root = new FrameLayout(host);
        root.setBackgroundColor(ScutTheme.BG);

        LinearLayout column = new LinearLayout(host);
        column.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(host);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        IconView close = new IconView(host, IconView.CLOSE, ScutTheme.TEXT);
        close.setPadding(ScutTheme.dp(host, 14), ScutTheme.dp(host, 14), ScutTheme.dp(host, 14), ScutTheme.dp(host, 14));
        ScutTheme.pressAlpha(close);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!importing) {
                    PickerScreen.this.host.pop();
                }
            }
        });
        top.addView(close, new LinearLayout.LayoutParams(ScutTheme.dp(host, 52), ScutTheme.dp(host, 52)));
        top.addView(ScutTheme.label(host, "Add videos", 18, ScutTheme.TEXT, true));
        column.addView(top, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ScutTheme.dp(host, 52)));

        LinearLayout tabs = new LinearLayout(host);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        String[] names = {"Videos", "Files"};
        for (int i = 0; i < 2; i++) {
            final int which = i;
            LinearLayout t = new LinearLayout(host);
            t.setOrientation(LinearLayout.VERTICAL);
            t.setGravity(Gravity.CENTER_HORIZONTAL);
            tabText[i] = ScutTheme.label(host, names[i], 15, ScutTheme.TEXT_DIM, true);
            tabText[i].setGravity(Gravity.CENTER);
            t.addView(tabText[i], new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            tabLine[i] = new View(host);
            t.addView(tabLine[i], new LinearLayout.LayoutParams(ScutTheme.dp(host, 28), ScutTheme.dp(host, 3)));
            ScutTheme.pressAlpha(t);
            t.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    setTab(which);
                }
            });
            tabs.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        column.addView(tabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ScutTheme.dp(host, 44)));

        FrameLayout body = new FrameLayout(host);
        grid = new GridView(host);
        grid.setNumColumns(4);
        grid.setHorizontalSpacing(gap);
        grid.setVerticalSpacing(gap);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                toggle(items.get(position).id);
            }
        });
        body.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        noVideos = ScutTheme.label(host, "No videos on this phone yet", 14, ScutTheme.TEXT_DIM, false);
        noVideos.setGravity(Gravity.CENTER);
        noVideos.setVisibility(View.GONE);
        body.addView(noVideos, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        needAccess = new LinearLayout(host);
        needAccess.setOrientation(LinearLayout.VERTICAL);
        needAccess.setGravity(Gravity.CENTER);
        needAccess.setBackgroundColor(ScutTheme.BG);
        needAccess.setPadding(ScutTheme.dp(host, 32), 0, ScutTheme.dp(host, 32), 0);
        needAccess.addView(ScutTheme.label(host, "Allow access to your videos", 18, ScutTheme.TEXT, true));
        TextView why = ScutTheme.label(host, "SCUT only reads the videos you choose. Nothing leaves your phone.", 14,
                ScutTheme.TEXT_DIM, false);
        why.setGravity(Gravity.CENTER);
        why.setPadding(0, ScutTheme.dp(host, 8), 0, ScutTheme.dp(host, 20));
        needAccess.addView(why);
        accessButton = ScutTheme.pill(host, "Allow access", ScutTheme.ACCENT, ScutTheme.ON_ACCENT);
        accessButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                askAccess();
            }
        });
        needAccess.addView(accessButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ScutTheme.dp(host, 44)));
        body.addView(needAccess, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        filesPanel = new LinearLayout(host);
        filesPanel.setOrientation(LinearLayout.VERTICAL);
        filesPanel.setGravity(Gravity.CENTER);
        filesPanel.setBackgroundColor(ScutTheme.BG);
        filesPanel.addView(new IconView(host, IconView.FOLDER, ScutTheme.TEXT_DIM),
                new LinearLayout.LayoutParams(ScutTheme.dp(host, 48), ScutTheme.dp(host, 48)));
        TextView filesHint = ScutTheme.label(host, "Pick videos from any folder", 16, ScutTheme.TEXT, true);
        filesHint.setPadding(0, ScutTheme.dp(host, 12), 0, ScutTheme.dp(host, 20));
        filesPanel.addView(filesHint);
        TextView browse = ScutTheme.pill(host, "Browse files", ScutTheme.ACCENT, ScutTheme.ON_ACCENT);
        browse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                browseFiles();
            }
        });
        filesPanel.addView(browse, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ScutTheme.dp(host, 44)));
        body.addView(filesPanel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        column.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout bottom = new LinearLayout(host);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setGravity(Gravity.CENTER_VERTICAL);
        bottom.setBackgroundColor(ScutTheme.BG);
        bottom.setPadding(ScutTheme.dp(host, 20), 0, ScutTheme.dp(host, 20), 0);
        bottom.setElevation(ScutTheme.dp(host, 6));
        countText = ScutTheme.label(host, "", 14, ScutTheme.TEXT_DIM, false);
        bottom.addView(countText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        addButton = ScutTheme.pill(host, "Add", ScutTheme.ACCENT, ScutTheme.ON_ACCENT);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startImport();
            }
        });
        bottom.addView(addButton, new LinearLayout.LayoutParams(ScutTheme.dp(host, 96), ScutTheme.dp(host, 40)));
        column.addView(bottom, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ScutTheme.dp(host, 64)));

        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        busy = new FrameLayout(host);
        busy.setBackgroundColor(0xE6FFFFFF);
        busy.setClickable(true);
        busy.setVisibility(View.GONE);
        busyText = ScutTheme.label(host, "", 16, ScutTheme.TEXT, true);
        busy.addView(busyText, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        root.addView(busy, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setTab(TAB_VIDEOS);
        updateBottom();
    }

    @Override
    public View view() {
        return root;
    }

    @Override
    public void onShow() {
        refresh();
    }

    @Override
    public boolean onBack() {
        return importing;
    }

    @Override
    public void onPermission(int request, String[] permissions, int[] results) {
        if (request != REQ_PERMISSION) {
            return;
        }
        boolean ok = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
        blocked = !ok && !host.shouldShowRequestPermissionRationale(MediaLibrary.permission());
        refresh();
    }

    @Override
    public void onResult(int request, int result, Intent data) {
        if (request != REQ_FILES || result != Activity.RESULT_OK || data == null) {
            return;
        }
        List<Uri> uris = new ArrayList<Uri>();
        ClipData clip = data.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                uris.add(clip.getItemAt(i).getUri());
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        for (Uri u : uris) {
            try {
                host.getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (RuntimeException ignored) {
            }
            if (!extra.contains(u)) {
                extra.add(u);
            }
        }
        updateBottom();
        startImport();
    }

    private void setTab(int which) {
        tab = which;
        for (int i = 0; i < 2; i++) {
            boolean on = i == which;
            tabText[i].setTextColor(on ? ScutTheme.TEXT : ScutTheme.TEXT_DIM);
            tabLine[i].setBackgroundColor(on ? ScutTheme.ACCENT : 0);
        }
        filesPanel.setVisibility(which == TAB_FILES ? View.VISIBLE : View.GONE);
        grid.setVisibility(which == TAB_VIDEOS ? View.VISIBLE : View.GONE);
        refresh();
    }

    private void refresh() {
        boolean ok = MediaLibrary.hasPermission(host);
        needAccess.setVisibility(!ok && tab == TAB_VIDEOS ? View.VISIBLE : View.GONE);
        accessButton.setText(blocked ? "Open settings" : "Allow access");
        if (!ok) {
            noVideos.setVisibility(View.GONE);
            return;
        }
        sv.io.execute(new Runnable() {
            @Override
            public void run() {
                final List<MediaLibrary.Item> found = MediaLibrary.queryVideos(sv.app);
                sv.main.post(new Runnable() {
                    @Override
                    public void run() {
                        items = found;
                        noVideos.setVisibility(found.isEmpty() && tab == TAB_VIDEOS ? View.VISIBLE : View.GONE);
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }

    private void askAccess() {
        if (blocked) {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + host.getPackageName()));
            host.startActivity(i);
            return;
        }
        host.requestPermissions(new String[] {MediaLibrary.permission()}, REQ_PERMISSION);
    }

    private void browseFiles() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("video/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        host.startActivityForResult(i, REQ_FILES);
    }

    private void toggle(long id) {
        int at = picked.indexOf(id);
        if (at >= 0) {
            picked.remove(at);
        } else {
            picked.add(id);
        }
        updateBottom();
        adapter.notifyDataSetChanged();
    }

    private void updateBottom() {
        int n = picked.size() + extra.size();
        countText.setText(n == 0 ? "Select videos" : n + (n == 1 ? " video selected" : " videos selected"));
        boolean on = n > 0;
        addButton.setBackground(ScutTheme.box(on ? ScutTheme.ACCENT : ScutTheme.SURFACE2, 0, 0, ScutTheme.dp(host, 20)));
        addButton.setTextColor(on ? ScutTheme.ON_ACCENT : ScutTheme.TEXT_DIM);
        addButton.setText(on ? "Add (" + n + ")" : "Add");
    }

    private MediaLibrary.Item find(long id) {
        for (MediaLibrary.Item it : items) {
            if (it.id == id) {
                return it;
            }
        }
        return null;
    }

    private void startImport() {
        if (importing) {
            return;
        }
        final ArrayList<Uri> uris = new ArrayList<Uri>();
        for (Long id : picked) {
            MediaLibrary.Item it = find(id);
            if (it != null) {
                uris.add(it.uri);
            }
        }
        uris.addAll(extra);
        if (uris.isEmpty()) {
            return;
        }
        importing = true;
        busy.setVisibility(View.VISIBLE);
        busyText.setText("Importing 0/" + uris.size());
        sv.io.execute(new Runnable() {
            @Override
            public void run() {
                final ArrayList<Clip> clips = new ArrayList<Clip>();
                final ArrayList<String> problems = new ArrayList<String>();
                for (int i = 0; i < uris.size(); i++) {
                    try {
                        clips.add(MetadataReader.toClip(MetadataReader.read(sv.app, uris.get(i))));
                    } catch (Exception e) {
                        problems.add(e.getMessage() == null ? "can't read a video" : e.getMessage());
                    }
                    final int done = i + 1;
                    sv.main.post(new Runnable() {
                        @Override
                        public void run() {
                            busyText.setText("Importing " + done + "/" + uris.size());
                        }
                    });
                }
                sv.main.post(new Runnable() {
                    @Override
                    public void run() {
                        importing = false;
                        busy.setVisibility(View.GONE);
                        extra.clear();
                        if (clips.isEmpty()) {
                            Toast.makeText(host, problems.isEmpty() ? "nothing to add" : problems.get(0),
                                    Toast.LENGTH_LONG).show();
                            updateBottom();
                            return;
                        }
                        if (!problems.isEmpty()) {
                            Toast.makeText(host, problems.size() + " video(s) skipped: " + problems.get(0),
                                    Toast.LENGTH_LONG).show();
                        }
                        callback.onPicked(clips);
                    }
                });
            }
        });
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Cell c = convert instanceof Cell ? (Cell) convert : new Cell(host);
            c.setLayoutParams(new AbsListView.LayoutParams(cell, cell));
            MediaLibrary.Item it = items.get(position);
            c.bind(it, picked.indexOf(it.id) + 1);
            return c;
        }
    }

    private final class Cell extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF chip = new RectF();
        private final Runnable redraw = new Runnable() {
            @Override
            public void run() {
                invalidate();
            }
        };
        private MediaLibrary.Item item;
        private int number;
        private String badge = "";

        Cell(Context c) {
            super(c);
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, c.getResources().getDisplayMetrics()));
        }

        void bind(MediaLibrary.Item it, int number) {
            this.item = it;
            this.number = number;
            this.badge = Fmt.badge(it.durationMs);
            String key = "g" + it.id;
            if (sv.thumbs.peek(key) == null) {
                sv.thumbs.poster(key, it.uri.toString(), Math.min(1000L, it.durationMs / 2) * 1000L, 256, 256, redraw);
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int w = getWidth();
            int h = getHeight();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ScutTheme.SURFACE2);
            c.drawRect(0, 0, w, h, paint);
            if (item == null) {
                return;
            }
            Bitmap b = sv.thumbs.peek("g" + item.id);
            if (b != null) {
                rect.set(0, 0, w, h);
                c.drawBitmap(b, null, rect, paint);
            }

            Context ctx = getContext();
            float pad = ScutTheme.dp(ctx, 5);
            float tw = paint.measureText(badge);
            float ph = ScutTheme.dp(ctx, 18);
            chip.set(w - pad - tw - pad, h - pad - ph, w - pad, h - pad);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(0x99000000);
            c.drawRoundRect(chip, ph / 2f, ph / 2f, paint);
            paint.setColor(0xFFFFFFFF);
            paint.setTextAlign(Paint.Align.LEFT);
            c.drawText(badge, chip.left + pad, chip.centerY() + paint.getTextSize() * 0.35f, paint);

            if (number > 0) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(ScutTheme.dp(ctx, 3));
                paint.setColor(ScutTheme.ACCENT);
                float half = paint.getStrokeWidth() / 2f;
                c.drawRect(half, half, w - half, h - half, paint);
            }

            float r = ScutTheme.dp(ctx, 10);
            float cx = w - pad - r;
            float cy = pad + r;
            if (number > 0) {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ScutTheme.ACCENT);
                c.drawCircle(cx, cy, r, paint);
                paint.setColor(0xFFFFFFFF);
                paint.setTextAlign(Paint.Align.CENTER);
                c.drawText(String.valueOf(number), cx, cy - (paint.descent() + paint.ascent()) / 2f, paint);
            } else {
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(0x55000000);
                c.drawCircle(cx, cy, r, paint);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(ScutTheme.dp(ctx, 1.5f));
                paint.setColor(0xFFFFFFFF);
                c.drawCircle(cx, cy, r, paint);
            }
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.LEFT);
        }
    }
}
