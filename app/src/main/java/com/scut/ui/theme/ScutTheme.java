package com.scut.ui.theme;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;

public final class ScutTheme {

    public static final int BG = 0xFFFFFFFF;
    public static final int SURFACE = 0xFFF4F4F6;
    public static final int SURFACE2 = 0xFFE9E9ED;
    public static final int TEXT = 0xFF111111;
    public static final int TEXT_DIM = 0xFF777780;
    public static final int ACCENT = 0xFF2A6BFF;
    public static final int ON_ACCENT = 0xFFFFFFFF;
    public static final int DIVIDER = 0xFFE4E4E8;
    public static final int DANGER = 0xFFE5384F;
    public static final int CANVAS = 0xFF000000;
    public static final int SCRIM = 0x66000000;

    private ScutTheme() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable box(int fill, int stroke, int strokeWidthPx, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        if (strokeWidthPx > 0) {
            d.setStroke(strokeWidthPx, stroke);
        }
        d.setCornerRadius(radiusPx);
        return d;
    }

    public static GradientDrawable topRounded(int fill, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadii(new float[] {radiusPx, radiusPx, radiusPx, radiusPx, 0, 0, 0, 0});
        return d;
    }

    public static TextView label(Context context, String text, float sp, int color, boolean medium) {
        TextView v = new TextView(context);
        v.setText(text);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        if (medium) {
            v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
        return v;
    }

    public static TextView pill(Context context, String text, int fill, int textColor) {
        TextView v = label(context, text, 14, textColor, true);
        v.setGravity(android.view.Gravity.CENTER);
        v.setPadding(dp(context, 16), 0, dp(context, 16), 0);
        v.setBackground(box(fill, 0, 0, dp(context, 18)));
        pressAlpha(v);
        return v;
    }

    public static void pressAlpha(final View view) {
        view.setClickable(true);
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                int a = e.getActionMasked();
                if (a == MotionEvent.ACTION_DOWN) {
                    v.setAlpha(0.6f);
                } else if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                    v.setAlpha(1f);
                }
                return false;
            }
        });
    }
}
