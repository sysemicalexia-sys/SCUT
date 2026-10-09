package com.scut.ui.editor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;

import com.scut.ui.theme.ScutTheme;

public final class PreviewBox extends ViewGroup {

    private final TextureView texture;
    private final Paint paint = new Paint();
    private final RectF canvasRect = new RectF();
    private final int margin;
    private float canvasAspect = 9f / 16f;
    private float clipAspect = 9f / 16f;

    public PreviewBox(Context context, TextureView texture) {
        super(context);
        this.texture = texture;
        this.margin = ScutTheme.dp(context, 12);
        paint.setColor(ScutTheme.CANVAS);
        setWillNotDraw(false);
        addView(texture, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    public void setAspects(float canvas, float clip) {
        if (canvas <= 0f || clip <= 0f) {
            return;
        }
        canvasAspect = canvas;
        clipAspect = clip;
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(View.resolveSize(0, widthSpec), View.resolveSize(0, heightSpec));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        float w = r - l;
        float h = b - t;
        float aw = w - 2 * margin;
        float ah = h - 2 * margin;
        if (aw <= 0 || ah <= 0) {
            canvasRect.setEmpty();
            texture.layout(0, 0, 0, 0);
            return;
        }
        float cw;
        float ch;
        if (aw / ah > canvasAspect) {
            ch = ah;
            cw = ch * canvasAspect;
        } else {
            cw = aw;
            ch = cw / canvasAspect;
        }
        float cx = w / 2f;
        float cy = h / 2f;
        canvasRect.set(cx - cw / 2f, cy - ch / 2f, cx + cw / 2f, cy + ch / 2f);

        float vw;
        float vh;
        if (clipAspect > canvasAspect) {
            vw = cw;
            vh = vw / clipAspect;
        } else {
            vh = ch;
            vw = vh * clipAspect;
        }
        int iw = Math.max(1, Math.round(vw));
        int ih = Math.max(1, Math.round(vh));
        int left = Math.round(cx - iw / 2f);
        int top = Math.round(cy - ih / 2f);
        texture.measure(View.MeasureSpec.makeMeasureSpec(iw, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(ih, View.MeasureSpec.EXACTLY));
        texture.layout(left, top, left + iw, top + ih);
    }

    @Override
    protected void onDraw(Canvas c) {
        c.drawRect(canvasRect, paint);
    }
}
