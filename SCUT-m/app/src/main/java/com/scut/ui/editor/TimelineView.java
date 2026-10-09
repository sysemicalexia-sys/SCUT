package com.scut.ui.editor;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;

import com.scut.media.Thumbs;
import com.scut.project.Clip;
import com.scut.project.Fmt;
import com.scut.project.Timeline;
import com.scut.ui.theme.ScutTheme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TimelineView extends HorizontalScrollView {

    public interface Listener {
        void onScrubStart();

        void onScrub(long timelineMs);

        void onScrubEnd();

        void onClipTap(int index);

        void onAdd();
    }

    private static final long[] STEPS = {
            100, 200, 500, 1000, 2000, 5000, 10000, 15000, 30000, 60000, 120000, 300000, 600000
    };

    private final Thumbs thumbs;
    private final Listener listener;
    private final Track track;
    private final ScaleGestureDetector scale;
    private final float minPx;
    private final float maxPx;
    private final float density;

    private List<Clip> clips = new ArrayList<Clip>();
    private int selected = -1;
    private float pxPerMs;
    private int viewport;
    private long restoreMs = -1;
    private boolean programmatic;
    private boolean ignoreRest;

    public TimelineView(Context context, Thumbs thumbs, Listener listener) {
        super(context);
        this.thumbs = thumbs;
        this.listener = listener;
        density = context.getResources().getDisplayMetrics().density;
        pxPerMs = 50f * density / 1000f;
        minPx = 6f * density / 1000f;
        maxPx = 400f * density / 1000f;
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        track = new Track(context);
        addView(track, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        scale = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                zoom(d.getScaleFactor());
                return true;
            }
        });
    }

    public static int heightDp() {
        return 112;
    }

    public void setClips(List<Clip> list, int selectedIndex, long timeMs) {
        clips = list;
        selected = selectedIndex;
        restoreMs = timeMs;
        track.requestLayout();
        track.invalidate();
    }

    public void setSelected(int index) {
        selected = index;
        track.invalidate();
    }

    public void setPlayhead(long ms) {
        programmatic = true;
        scrollTo(Math.round(Math.max(0, ms) * pxPerMs), 0);
        programmatic = false;
    }

    public long playhead() {
        long total = Timeline.total(clips);
        return Math.max(0, Math.min(total, (long) (getScrollX() / pxPerMs)));
    }

    private void zoom(float factor) {
        float next = Math.max(minPx, Math.min(maxPx, pxPerMs * factor));
        if (next == pxPerMs) {
            return;
        }
        restoreMs = playhead();
        pxPerMs = next;
        track.requestLayout();
        track.invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewport = w;
        track.requestLayout();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (restoreMs >= 0 && viewport > 0 && track.getWidth() == track.expectedWidth()) {
            long ms = restoreMs;
            restoreMs = -1;
            setPlayhead(ms);
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        if (!programmatic) {
            listener.onScrub(playhead());
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        scale.onTouchEvent(ev);
        int action = ev.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            ignoreRest = false;
            listener.onScrubStart();
        }
        // pinch, cancel what the scroll view started and ignore the rest of the gesture
        if (scale.isInProgress() || ev.getPointerCount() > 1) {
            if (!ignoreRest) {
                ignoreRest = true;
                MotionEvent cancel = MotionEvent.obtain(ev);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
            }
            return true;
        }
        if (ignoreRest) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                ignoreRest = false;
                listener.onScrubEnd();
            }
            return true;
        }
        boolean handled = super.dispatchTouchEvent(ev);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            listener.onScrubEnd();
        }
        return handled;
    }

    private final class Track extends View {

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF rect = new RectF();
        private final RectF dst = new RectF();
        private final Path shape = new Path();
        private final GestureDetector taps;
        private final float rulerH;
        private final float trackTop;
        private final float trackH;
        private final float addW;
        private final float radius;
        private boolean redrawPosted;
        private final Runnable redraw = new Runnable() {
            @Override
            public void run() {
                redrawPosted = false;
                invalidate();
            }
        };
        private final Runnable thumbDone = new Runnable() {
            @Override
            public void run() {
                if (!redrawPosted) {
                    redrawPosted = true;
                    post(redraw);
                }
            }
        };

        Track(Context c) {
            super(c);
            rulerH = ScutTheme.dp(c, 26);
            trackTop = ScutTheme.dp(c, 34);
            trackH = ScutTheme.dp(c, 64);
            addW = ScutTheme.dp(c, 72);
            radius = ScutTheme.dp(c, 8);
            text.setTextSize(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 10, c.getResources().getDisplayMetrics()));
            taps = new GestureDetector(c, new GestureDetector.SimpleOnGestureListener() {
                @Override
                public boolean onSingleTapUp(MotionEvent e) {
                    tap(e.getX(), e.getY());
                    return true;
                }
            });
        }

        private int leftPad() {
            return viewport / 2;
        }

        private int clipsWidth() {
            return (int) (Timeline.total(clips) * pxPerMs);
        }

        int expectedWidth() {
            return viewport + clipsWidth() + (int) addW;
        }

        @Override
        protected void onMeasure(int widthSpec, int heightSpec) {
            setMeasuredDimension(expectedWidth(), ScutTheme.dp(getContext(), heightDp()));
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            taps.onTouchEvent(e);
            return true;
        }

        private void tap(float x, float y) {
            float addStart = leftPad() + clipsWidth();
            if (x >= addStart && x <= addStart + addW) {
                listener.onAdd();
                return;
            }
            if (y < trackTop || y > trackTop + trackH) {
                listener.onClipTap(-1);
                return;
            }
            long t = (long) ((x - leftPad()) / pxPerMs);
            if (t < 0 || t >= Timeline.total(clips)) {
                listener.onClipTap(-1);
                return;
            }
            Timeline.Spot s = Timeline.locate(clips, t);
            listener.onClipTap(s == null ? -1 : s.index);
        }

        @Override
        protected void onDraw(Canvas c) {
            Rect vis = c.getClipBounds();
            drawRuler(c, vis.left, vis.right);
            drawClips(c, vis.left, vis.right);
            drawAdd(c);
        }

        private long rulerStep() {
            float want = ScutTheme.dp(getContext(), 64);
            for (long s : STEPS) {
                if (s * pxPerMs >= want) {
                    return s;
                }
            }
            return STEPS[STEPS.length - 1];
        }

        private void drawRuler(Canvas c, int vl, int vr) {
            long step = rulerStep();
            long end = Timeline.total(clips) + step * 2;
            long first = Math.max(0, ((long) ((vl - leftPad()) / pxPerMs) / step - 1) * step);
            float major = ScutTheme.dp(getContext(), 7);
            float minor = ScutTheme.dp(getContext(), 4);
            float base = rulerH - ScutTheme.dp(getContext(), 4);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(ScutTheme.dp(getContext(), 1));
            paint.setColor(0xFFB8B8C0);
            text.setColor(ScutTheme.TEXT_DIM);
            for (long t = first; t <= end; t += step) {
                float x = leftPad() + t * pxPerMs;
                if (x > vr + 80) {
                    break;
                }
                if (x < vl - 80) {
                    continue;
                }
                c.drawLine(x, base - major, x, base, paint);
                String label = step >= 1000 ? Fmt.clock(t) : String.format(Locale.US, "%.1fs", t / 1000f);
                c.drawText(label, x + ScutTheme.dp(getContext(), 4), base - major + ScutTheme.dp(getContext(), 7), text);
                for (int k = 1; k < 4; k++) {
                    float xm = x + step * pxPerMs * k / 4f;
                    c.drawLine(xm, base - minor, xm, base, paint);
                }
            }
            paint.setStyle(Paint.Style.FILL);
        }

        private void drawClips(Canvas c, int vl, int vr) {
            long start = 0;
            float top = trackTop;
            float bottom = trackTop + trackH;
            float gap = ScutTheme.dp(getContext(), 1);
            for (int i = 0; i < clips.size(); i++) {
                Clip clip = clips.get(i);
                float x0 = leftPad() + start * pxPerMs;
                float x1 = x0 + clip.lengthMs() * pxPerMs;
                start += clip.lengthMs();
                if (x1 < vl || x0 > vr) {
                    continue;
                }
                rect.set(x0 + gap, top, x1 - gap, bottom);
                shape.reset();
                shape.addRoundRect(rect, radius, radius, Path.Direction.CW);
                c.save();
                c.clipPath(shape);
                paint.setStyle(Paint.Style.FILL);
                paint.setColor(ScutTheme.SURFACE2);
                c.drawRect(rect, paint);
                drawTiles(c, clip, x0, x1, vl, vr, top, bottom);
                c.restore();

                if (i == selected) {
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(ScutTheme.dp(getContext(), 2.5f));
                    paint.setColor(ScutTheme.ACCENT);
                    float half = paint.getStrokeWidth() / 2f;
                    dst.set(rect.left + half, rect.top + half, rect.right - half, rect.bottom - half);
                    c.drawRoundRect(dst, radius, radius, paint);
                    paint.setStyle(Paint.Style.FILL);
                }

                if (x1 - x0 > ScutTheme.dp(getContext(), 56)) {
                    String s = Fmt.length(clip.lengthMs());
                    float pad = ScutTheme.dp(getContext(), 5);
                    float tw = text.measureText(s);
                    float ch = ScutTheme.dp(getContext(), 16);
                    float cx = Math.max(x0, vl) + ScutTheme.dp(getContext(), 8);
                    if (cx + tw + 2 * pad < x1) {
                        dst.set(cx, top + ScutTheme.dp(getContext(), 6), cx + tw + 2 * pad, top + ScutTheme.dp(getContext(), 6) + ch);
                        paint.setColor(0x88000000);
                        c.drawRoundRect(dst, ch / 2f, ch / 2f, paint);
                        text.setColor(0xFFFFFFFF);
                        c.drawText(s, dst.left + pad, dst.centerY() + text.getTextSize() * 0.35f, text);
                    }
                }
            }
        }

        private void drawTiles(Canvas c, Clip clip, float x0, float x1, int vl, int vr, float top, float bottom) {
            float tile = trackH;
            int px = Math.max(96, Math.round(trackH));
            float from = Math.max(x0, vl);
            float to = Math.min(x1, vr);
            for (int k = (int) ((from - x0) / tile); ; k++) {
                float tx = x0 + k * tile;
                if (tx > to) {
                    break;
                }
                long srcMs = clip.inMs + (long) ((k * tile + tile / 2f) / pxPerMs);
                srcMs = Math.max(0, Math.min(srcMs, Math.max(0, clip.sourceMs - 1)));
                String key = clip.uri + "#" + (srcMs / 1000);
                Bitmap b = thumbs.peek(key);
                if (b != null) {
                    dst.set(tx, top, tx + tile, bottom);
                    c.drawBitmap(b, null, dst, paint);
                } else {
                    long at = Math.min(srcMs / 1000 * 1000 + 500, Math.max(0, clip.sourceMs - 1));
                    thumbs.frame(key, clip.uri, at * 1000L, px, thumbDone);
                }
            }
        }

        private void drawAdd(Canvas c) {
            float cx = leftPad() + clipsWidth() + addW / 2f;
            float cy = trackTop + trackH / 2f;
            float r = ScutTheme.dp(getContext(), 20);
            rect.set(cx - r, cy - r, cx + r, cy + r);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ScutTheme.BG);
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(ScutTheme.dp(getContext(), 1));
            paint.setColor(ScutTheme.DIVIDER);
            c.drawRoundRect(rect, radius, radius, paint);
            paint.setStrokeWidth(ScutTheme.dp(getContext(), 2));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(ScutTheme.TEXT);
            float arm = ScutTheme.dp(getContext(), 7);
            c.drawLine(cx - arm, cy, cx + arm, cy, paint);
            c.drawLine(cx, cy - arm, cx, cy + arm, paint);
            paint.setStrokeCap(Paint.Cap.BUTT);
            paint.setStyle(Paint.Style.FILL);
        }
    }
}
