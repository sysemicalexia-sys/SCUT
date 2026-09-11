package com.scut.engine;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

public class MathRenderer {
    public static Bitmap compileToBitmap(String latexInput, int textColor, float textSize) {
        // placeholder for jlatexmath integration
        int width = (int) (latexInput.length() * textSize * 0.6f);
        int height = (int) (textSize * 1.5f);
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        Paint p = new Paint();
        p.setColor(textColor);
        p.setTextSize(textSize);
        c.drawText(latexInput, 10, height / 2f, p);
        return bmp;
    }
}
