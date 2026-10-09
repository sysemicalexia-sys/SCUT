package com.scut.ui.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

import com.scut.ui.theme.ScutTheme;

public final class IconView extends View {

    public static final int CLOSE = 1;
    public static final int BACK = 2;
    public static final int CHEVRON_DOWN = 3;
    public static final int PLAY = 4;
    public static final int PAUSE = 5;
    public static final int PLUS = 6;
    public static final int UNDO = 7;
    public static final int REDO = 8;
    public static final int SPLIT = 9;
    public static final int TRASH = 10;
    public static final int VOLUME = 11;
    public static final int MUTE = 12;
    public static final int INFO = 13;
    public static final int CHECK = 14;
    public static final int SHARE = 15;
    public static final int MORE = 16;
    public static final int SCISSORS = 17;
    public static final int MUSIC = 18;
    public static final int TEXT = 19;
    public static final int SPARKLE = 20;
    public static final int FILTERS = 21;
    public static final int CANVAS = 22;
    public static final int FOLDER = 23;
    public static final int DOWNLOAD = 24;

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private int icon;

    public IconView(Context context, int icon, int color) {
        super(context);
        this.icon = icon;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setStrokeWidth(2f);
        fill.setStyle(Paint.Style.FILL);
        setColor(color);
    }

    public void setIcon(int icon) {
        this.icon = icon;
        invalidate();
    }

    public void setColor(int color) {
        stroke.setColor(color);
        fill.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float availW = getWidth() - getPaddingLeft() - getPaddingRight();
        float availH = getHeight() - getPaddingTop() - getPaddingBottom();
        float s = Math.min(availW, availH) / 24f;
        if (s <= 0) {
            return;
        }
        c.save();
        c.translate(getPaddingLeft() + (availW - 24f * s) / 2f, getPaddingTop() + (availH - 24f * s) / 2f);
        c.scale(s, s);
        draw24(c);
        c.restore();
    }

    private void line(Canvas c, float x1, float y1, float x2, float y2) {
        c.drawLine(x1, y1, x2, y2, stroke);
    }

    private void poly(Canvas c, boolean close, boolean filled, float... pts) {
        path.reset();
        path.moveTo(pts[0], pts[1]);
        for (int i = 2; i < pts.length; i += 2) {
            path.lineTo(pts[i], pts[i + 1]);
        }
        if (close) {
            path.close();
        }
        c.drawPath(path, filled ? fill : stroke);
    }

    private void box(Canvas c, float l, float t, float r, float b, float rad, boolean filled) {
        rect.set(l, t, r, b);
        c.drawRoundRect(rect, rad, rad, filled ? fill : stroke);
    }

    private void draw24(Canvas c) {
        switch (icon) {
            case CLOSE:
                line(c, 6, 6, 18, 18);
                line(c, 18, 6, 6, 18);
                break;
            case BACK:
                poly(c, false, false, 15, 5, 8, 12, 15, 19);
                break;
            case CHEVRON_DOWN:
                poly(c, false, false, 6, 9, 12, 15, 18, 9);
                break;
            case PLAY:
                poly(c, true, true, 8, 5, 19, 12, 8, 19);
                break;
            case PAUSE:
                box(c, 6.5f, 5, 10.5f, 19, 1.2f, true);
                box(c, 13.5f, 5, 17.5f, 19, 1.2f, true);
                break;
            case PLUS:
                line(c, 12, 5, 12, 19);
                line(c, 5, 12, 19, 12);
                break;
            case UNDO:
                drawUndo(c);
                break;
            case REDO:
                c.save();
                c.scale(-1f, 1f, 12f, 12f);
                drawUndo(c);
                c.restore();
                break;
            case SPLIT:
                line(c, 12, 3, 12, 21);
                box(c, 3.5f, 8, 9, 16, 1.5f, false);
                box(c, 15, 8, 20.5f, 16, 1.5f, false);
                break;
            case TRASH:
                line(c, 4.5f, 7, 19.5f, 7);
                poly(c, false, false, 9.5f, 7, 9.5f, 4, 14.5f, 4, 14.5f, 7);
                poly(c, false, false, 6.5f, 7, 7.5f, 20, 16.5f, 20, 17.5f, 7);
                line(c, 10, 10.5f, 10, 16.5f);
                line(c, 14, 10.5f, 14, 16.5f);
                break;
            case VOLUME:
                speaker(c);
                path.reset();
                path.moveTo(16, 9);
                path.quadTo(19, 12, 16, 15);
                c.drawPath(path, stroke);
                path.reset();
                path.moveTo(18.5f, 6.5f);
                path.quadTo(23, 12, 18.5f, 17.5f);
                c.drawPath(path, stroke);
                break;
            case MUTE:
                speaker(c);
                line(c, 16.5f, 9.5f, 21.5f, 14.5f);
                line(c, 21.5f, 9.5f, 16.5f, 14.5f);
                break;
            case INFO:
                c.drawCircle(12, 12, 9, stroke);
                c.drawCircle(12, 8, 1.2f, fill);
                line(c, 12, 11.5f, 12, 16.5f);
                break;
            case CHECK:
                poly(c, false, false, 5, 13, 10, 18, 19, 7);
                break;
            case SHARE:
                line(c, 12, 15, 12, 3.5f);
                poly(c, false, false, 8, 7.5f, 12, 3.5f, 16, 7.5f);
                poly(c, false, false, 5, 12, 5, 20, 19, 20, 19, 12);
                break;
            case MORE:
                c.drawCircle(5, 12, 1.8f, fill);
                c.drawCircle(12, 12, 1.8f, fill);
                c.drawCircle(19, 12, 1.8f, fill);
                break;
            case SCISSORS:
                c.drawCircle(7, 18, 2.6f, stroke);
                c.drawCircle(17, 18, 2.6f, stroke);
                line(c, 8.6f, 16, 17, 4);
                line(c, 15.4f, 16, 7, 4);
                break;
            case MUSIC:
                c.drawCircle(8, 18, 2.6f, fill);
                line(c, 10.6f, 18, 10.6f, 5);
                poly(c, false, false, 10.6f, 5, 18, 3.5f, 18, 7, 10.6f, 8.5f);
                break;
            case TEXT:
                line(c, 5, 6, 19, 6);
                line(c, 12, 6, 12, 19);
                break;
            case SPARKLE:
                poly(c, true, false, 12, 3, 14, 10, 21, 12, 14, 14, 12, 21, 10, 14, 3, 12, 10, 10);
                break;
            case FILTERS:
                c.drawCircle(9, 10, 5.5f, stroke);
                c.drawCircle(15, 10, 5.5f, stroke);
                c.drawCircle(12, 15, 5.5f, stroke);
                break;
            case CANVAS:
                box(c, 4, 6, 20, 18, 2f, false);
                box(c, 8, 9.5f, 16, 14.5f, 1f, false);
                break;
            case FOLDER:
                poly(c, true, false, 3.5f, 7, 9.5f, 7, 11.5f, 9, 20.5f, 9, 20.5f, 19, 3.5f, 19);
                break;
            case DOWNLOAD:
                line(c, 12, 4, 12, 15);
                poly(c, false, false, 8, 11.5f, 12, 15.5f, 16, 11.5f);
                line(c, 5, 20, 19, 20);
                break;
            default:
                break;
        }
    }

    private void drawUndo(Canvas c) {
        poly(c, false, false, 9, 4, 4, 9, 9, 14);
        path.reset();
        path.moveTo(4, 9);
        path.lineTo(14, 9);
        path.quadTo(20, 9, 20, 14.5f);
        path.quadTo(20, 20, 14, 20);
        path.lineTo(9, 20);
        c.drawPath(path, stroke);
    }

    private void speaker(Canvas c) {
        poly(c, true, false, 4, 9.5f, 8, 9.5f, 13, 5.5f, 13, 18.5f, 8, 14.5f, 4, 14.5f);
    }

    public static IconView of(Context context, int icon, int color, int sizeDp) {
        IconView v = new IconView(context, icon, color);
        v.setMinimumWidth(ScutTheme.dp(context, sizeDp));
        v.setMinimumHeight(ScutTheme.dp(context, sizeDp));
        return v;
    }
}
