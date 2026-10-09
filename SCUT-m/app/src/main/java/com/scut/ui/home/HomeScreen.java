package com.scut.ui.home;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.scut.engine.MainActivity;
import com.scut.project.Clip;
import com.scut.project.Fmt;
import com.scut.project.Project;
import com.scut.ui.BaseScreen;
import com.scut.ui.Services;
import com.scut.ui.editor.EditorScreen;
import com.scut.ui.picker.PickerScreen;
import com.scut.ui.theme.ScutTheme;
import com.scut.ui.widgets.BottomSheet;
import com.scut.ui.widgets.IconView;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class HomeScreen extends BaseScreen {

    private final MainActivity host;
    private final Services sv;
    private final FrameLayout root;
    private final GridView grid;
    private final TextView count;
    private final LinearLayout empty;
    private final BottomSheet sheet;
    private final Adapter adapter = new Adapter();
    private final int cellW;
    private final int cellH;
    private final java.util.HashSet<String> posterBusy = new java.util.HashSet<String>();
    private List<Project> projects = new ArrayList<Project>();

    public HomeScreen(final MainActivity host) {
        this.host = host;
        this.sv = Services.get(host);

        int gap = ScutTheme.dp(host, 12);
        int side = ScutTheme.dp(host, 20);
        int screenW = host.getResources().getDisplayMetrics().widthPixels;
        cellW = (screenW - 2 * side - gap) / 2;
        cellH = cellW * 5 / 4 + ScutTheme.dp(host, 56);

        root = new FrameLayout(host);
        root.setBackgroundColor(ScutTheme.BG);

        LinearLayout column = new LinearLayout(host);
        column.setOrientation(LinearLayout.VERTICAL);

        TextView brand = ScutTheme.label(host, "SCUT", 24, ScutTheme.TEXT, true);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(side, 0, side, 0);
        column.addView(brand, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ScutTheme.dp(host, 64)));

        FrameLayout start = new FrameLayout(host);
        start.setBackground(ScutTheme.box(ScutTheme.ACCENT, 0, 0, ScutTheme.dp(host, 18)));
        LinearLayout startRow = new LinearLayout(host);
        startRow.setOrientation(LinearLayout.HORIZONTAL);
        startRow.setGravity(Gravity.CENTER);
        IconView plus = new IconView(host, IconView.PLUS, ScutTheme.ON_ACCENT);
        startRow.addView(plus, new LinearLayout.LayoutParams(ScutTheme.dp(host, 28), ScutTheme.dp(host, 28)));
        TextView startText = ScutTheme.label(host, "New project", 18, ScutTheme.ON_ACCENT, true);
        startText.setPadding(ScutTheme.dp(host, 8), 0, 0, 0);
        startRow.addView(startText, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        start.addView(startRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        ScutTheme.pressAlpha(start);
        start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startProject();
            }
        });
        LinearLayout.LayoutParams startParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ScutTheme.dp(host, 88));
        startParams.setMargins(side, ScutTheme.dp(host, 4), side, ScutTheme.dp(host, 20));
        column.addView(start, startParams);

        LinearLayout section = new LinearLayout(host);
        section.setOrientation(LinearLayout.HORIZONTAL);
        section.setGravity(Gravity.CENTER_VERTICAL);
        section.setPadding(side, 0, side, ScutTheme.dp(host, 10));
        section.addView(ScutTheme.label(host, "Drafts", 16, ScutTheme.TEXT, true));
        count = ScutTheme.label(host, "", 14, ScutTheme.TEXT_DIM, false);
        count.setPadding(ScutTheme.dp(host, 8), 0, 0, 0);
        section.addView(count);
        column.addView(section, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout body = new FrameLayout(host);
        grid = new GridView(host);
        grid.setNumColumns(2);
        grid.setHorizontalSpacing(gap);
        grid.setVerticalSpacing(gap);
        grid.setPadding(side, 0, side, side);
        grid.setClipToPadding(false);
        grid.setSelector(new android.graphics.drawable.ColorDrawable(0));
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                host.push(new EditorScreen(host, projects.get(position)));
            }
        });
        grid.setOnItemLongClickListener(new android.widget.AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                menu(projects.get(position));
                return true;
            }
        });
        body.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        empty = new LinearLayout(host);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        TextView e1 = ScutTheme.label(host, "No drafts yet", 16, ScutTheme.TEXT, true);
        TextView e2 = ScutTheme.label(host, "Tap New project and pick some videos", 14, ScutTheme.TEXT_DIM, false);
        e2.setPadding(0, ScutTheme.dp(host, 6), 15, 0);
		e1.setPadding(0, ScutTheme.dp(host , 7) , 15 , 0);
        empty.addView(e1);
        empty.addView(e2);
        body.addView(empty, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        column.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(column, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        sheet = new BottomSheet(host);
        root.addView(sheet, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    public View view() {
        return root;
    }

    private final Runnable posterChanged = new Runnable() {
        @Override
        public void run() {
            adapter.notifyDataSetChanged();
        }
    };

    @Override
    public void onHide() {
        sv.onPoster = null;
    }

    @Override
    public void onShow() {
        sv.onPoster = posterChanged;
        sv.disk.execute(new Runnable() {
            @Override
            public void run() {
                final List<Project> found = sv.store.list();
                sv.main.post(new Runnable() {
                    @Override
                    public void run() {
                        projects = found;
                        count.setText(found.isEmpty() ? "" : String.valueOf(found.size()));
                        empty.setVisibility(found.isEmpty() ? View.VISIBLE : View.GONE);
                        adapter.notifyDataSetChanged();
                    }
                });
            }
        });
    }

    @Override
    public boolean onBack() {
        if (sheet.isShowing()) {
            sheet.hide();
            return true;
        }
        return false;
    }

    private void startProject() {
        host.push(new PickerScreen(host, new PickerScreen.Callback() {
            @Override
            public void onPicked(List<Clip> clips) {
                Project p = new Project();
                p.id = Clip.newId() + Long.toString(System.currentTimeMillis(), 36);
                p.created = System.currentTimeMillis();
                p.modified = p.created;
                p.name = new SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(new Date(p.created));
                p.clips.addAll(clips);
                try {
                    sv.store.save(p);
                } catch (IOException ignored) {
                }
                sv.refreshPoster(p);
                host.replace(new EditorScreen(host, p));
            }
        }));
    }

    private void menu(final Project p) {
        LinearLayout list = new LinearLayout(host);
        list.setOrientation(LinearLayout.VERTICAL);
        list.addView(row("Rename", ScutTheme.TEXT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sheet.hide();
                rename(p);
            }
        }));
        list.addView(row("Delete", ScutTheme.DANGER, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sheet.hide();
                confirmDelete(p);
            }
        }));
        sheet.show(p.name, list);
    }

    private View row(String text, int color, View.OnClickListener l) {
        TextView t = ScutTheme.label(host, text, 16, color, false);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setMinHeight(ScutTheme.dp(host, 52));
        ScutTheme.pressAlpha(t);
        t.setOnClickListener(l);
        return t;
    }

    private void rename(final Project p) {
        final EditText input = new EditText(host);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setText(p.name);
        input.setSelection(p.name.length());
        new AlertDialog.Builder(host)
                .setTitle("Rename project")
                .setView(input)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        String n = input.getText().toString().trim();
                        if (n.length() > 0) {
                            p.name = n;
                            p.modified = System.currentTimeMillis();
                            sv.saveAsync(p);
                            adapter.notifyDataSetChanged();
                        }
                    }
                })
                .show();
    }

    private void confirmDelete(final Project p) {
        new AlertDialog.Builder(host)
                .setTitle("Delete project?")
                .setMessage("This removes the draft, not your videos.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        final String id = p.id;
                        projects.remove(p);
                        count.setText(projects.isEmpty() ? "" : String.valueOf(projects.size()));
                        empty.setVisibility(projects.isEmpty() ? View.VISIBLE : View.GONE);
                        adapter.notifyDataSetChanged();
                        sv.disk.execute(new Runnable() {
                            @Override
                            public void run() {
                                sv.store.delete(id);
                            }
                        });
                    }
                })
                .show();
    }

    private final class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return projects.size();
        }

        @Override
        public Object getItem(int position) {
            return projects.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convert, ViewGroup parent) {
            Cell cell = convert instanceof Cell ? (Cell) convert : new Cell(host);
            cell.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cellH));
            cell.bind(projects.get(position));
            return cell;
        }
    }

    private final class Cell extends FrameLayout {
        private final Thumb thumb;
        private final TextView name;
        private final TextView meta;
        private final IconView more;
        private Project bound;

        Cell(Context c) {
            super(c);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            thumb = new Thumb(c);
            col.addView(thumb, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cellW * 5 / 4));
            name = ScutTheme.label(c, "", 14, ScutTheme.TEXT, true);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(ScutTheme.dp(c, 2), ScutTheme.dp(c, 8), ScutTheme.dp(c, 2), 0);
            col.addView(name, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            meta = ScutTheme.label(c, "", 12, ScutTheme.TEXT_DIM, false);
            meta.setSingleLine(true);
            meta.setPadding(ScutTheme.dp(c, 2), ScutTheme.dp(c, 2), ScutTheme.dp(c, 2), 0);
            col.addView(meta, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            addView(col, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            more = new IconView(c, IconView.MORE, 0xFFFFFFFF);
            more.setPadding(ScutTheme.dp(c, 6), ScutTheme.dp(c, 6), ScutTheme.dp(c, 6), ScutTheme.dp(c, 6));
            more.setBackground(ScutTheme.box(0x66000000, 0, 0, ScutTheme.dp(c, 16)));
            ScutTheme.pressAlpha(more);
            more.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (bound != null) {
                        menu(bound);
                    }
                }
            });
            FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(
                    ScutTheme.dp(c, 32), ScutTheme.dp(c, 32), Gravity.TOP | Gravity.END);
            mp.setMargins(0, ScutTheme.dp(c, 8), ScutTheme.dp(c, 8), 0);
            addView(more, mp);
        }

        void bind(Project p) {
            bound = p;
            name.setText(p.name);
            meta.setText(Fmt.badge(p.totalMs()) + "  \u00b7  " + p.clips.size() + (p.clips.size() == 1 ? " clip" : " clips"));
            thumb.bind(p);
        }
    }

    private final class Thumb extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF chip = new RectF();
        private final float radius;
        private String key;
        private String duration = "";

        Thumb(Context c) {
            super(c);
            radius = ScutTheme.dp(c, 14);
            paint.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 11, c.getResources().getDisplayMetrics()));
        }

        void bind(Project p) {
            final File f = sv.store.posterFile(p.id);
            key = "p:" + p.id + ":" + f.lastModified();
            duration = Fmt.badge(p.totalMs());
            if (sv.thumbs.peek(key) == null && posterBusy.add(key)) {
                final String k = key;
                sv.io.execute(new Runnable() {
                    @Override
                    public void run() {
                        Bitmap b = null;
                        if (f.exists()) {
                            BitmapFactory.Options o = new BitmapFactory.Options();
                            o.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                            o.inJustDecodeBounds = false;
                            o.inSampleSize = Math.max(1, o.outWidth / 600);
                            b = BitmapFactory.decodeFile(f.getAbsolutePath(), o);
                            if (b != null) {
                                sv.thumbs.put(k, b);
                            }
                        }
                        sv.main.post(new Runnable() {
                            @Override
                            public void run() {
                                posterBusy.remove(k);
                                invalidate();
                            }
                        });
                    }
                });
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            rect.set(0, 0, getWidth(), getHeight());
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ScutTheme.SURFACE2);
            c.drawRoundRect(rect, radius, radius, paint);

            Bitmap b = key == null ? null : sv.thumbs.peek(key);
            if (b != null) {
                c.save();
                android.graphics.Path clip = new android.graphics.Path();
                clip.addRoundRect(rect, radius, radius, android.graphics.Path.Direction.CW);
                c.clipPath(clip);
                float s = Math.max(getWidth() / (float) b.getWidth(), getHeight() / (float) b.getHeight());
                float w = b.getWidth() * s;
                float h = b.getHeight() * s;
                rect.set((getWidth() - w) / 2f, (getHeight() - h) / 2f, (getWidth() + w) / 2f, (getHeight() + h) / 2f);
                c.drawBitmap(b, null, rect, paint);
                c.restore();
            }

            float pad = ScutTheme.dp(getContext(), 8);
            float tw = paint.measureText(duration);
            float ph = ScutTheme.dp(getContext(), 20);
            chip.set(getWidth() - pad - tw - pad, getHeight() - pad - ph, getWidth() - pad, getHeight() - pad);
            paint.setColor(0x99000000);
            c.drawRoundRect(chip, ph / 2f, ph / 2f, paint);
            paint.setColor(0xFFFFFFFF);
            c.drawText(duration, chip.left + pad, chip.centerY() + paint.getTextSize() * 0.35f, paint);
        }
    }
}
