package com.scut.ui.widgets;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.scut.ui.theme.ScutTheme;

public final class BottomSheet extends FrameLayout {

    private final View scrim;
    private final LinearLayout panel;
    private final LinearLayout body;
    private final TextView title;
    private boolean showing;

    public BottomSheet(Context context) {
        super(context);
        setVisibility(GONE);

        scrim = new View(context);
        scrim.setBackgroundColor(ScutTheme.SCRIM);
        scrim.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });
        addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(ScutTheme.topRounded(ScutTheme.BG, ScutTheme.dp(context, 18)));
        panel.setClickable(true);
        panel.setElevation(ScutTheme.dp(context, 16));

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(ScutTheme.dp(context, 20), 0, ScutTheme.dp(context, 8), 0);
        title = ScutTheme.label(context, "", 16, ScutTheme.TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        IconView close = new IconView(context, IconView.CLOSE, ScutTheme.TEXT);
        close.setPadding(ScutTheme.dp(context, 12), ScutTheme.dp(context, 12),
                ScutTheme.dp(context, 12), ScutTheme.dp(context, 12));
        ScutTheme.pressAlpha(close);
        close.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });
        header.addView(close, new LinearLayout.LayoutParams(ScutTheme.dp(context, 48), ScutTheme.dp(context, 48)));
        panel.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ScutTheme.dp(context, 52)));

        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(ScutTheme.dp(context, 20), 0, ScutTheme.dp(context, 20), ScutTheme.dp(context, 24));
        panel.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
    }

    public boolean isShowing() {
        return showing;
    }

    public void show(String heading, View content) {
        title.setText(heading);
        body.removeAllViews();
        body.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        showing = true;
        setVisibility(VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(180L);
        int wide = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        panel.measure(View.MeasureSpec.makeMeasureSpec(wide, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        panel.animate().cancel();
        panel.setTranslationY(panel.getMeasuredHeight());
        panel.animate().translationY(0f).setDuration(220L);
    }

    public void hide() {
        if (!showing) {
            return;
        }
        showing = false;
        scrim.animate().alpha(0f).setDuration(160L);
        panel.animate().cancel();
        panel.animate().translationY(panel.getHeight()).setDuration(200L).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (!showing) {
                    setVisibility(GONE);
                    body.removeAllViews();
                }
            }
        });
    }
}
