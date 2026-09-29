package com.scut.engine;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

public class MathRenderer {
    private static final int MAX_SIDE = 4096;
    private static final int MARGIN = 10;

    public static Bitmap compileToBitmap(String latexInput, int textColor, float textSize) {
        String text = latexInput == null ? "" : latexInput;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(textColor);
        p.setTextSize(Math.max(1f, Math.min(textSize, 512f)));

        int width = (int) Math.ceil(p.measureText(text)) + 2 * MARGIN;
        int height = (int) Math.ceil(p.descent() - p.ascent());
        width = Math.max(1, Math.min(width, MAX_SIDE));
        height = Math.max(1, Math.min(height, MAX_SIDE));

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        new Canvas(bmp).drawText(text, MARGIN, -p.ascent(), p);
        return bmp;
    }
}
