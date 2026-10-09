package com.scut.engine;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;

import com.scut.ui.Screen;
import com.scut.ui.Services;
import com.scut.ui.home.HomeScreen;
import com.scut.ui.theme.ScutTheme;

import java.util.ArrayList;

public class MainActivity extends Activity {

    private final ArrayList<Screen> stack = new ArrayList<Screen>();
    private FrameLayout root;
    private boolean resumedOnce;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Services.get(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(ScutTheme.BG);
        root.setFitsSystemWindows(true);
        setContentView(root);
        applyLightSystemBars();
        push(new HomeScreen(this));
    }

    private void applyLightSystemBars() {
        Window window = getWindow();
        window.setStatusBarColor(Color.WHITE);
        window.setNavigationBarColor(Color.WHITE);
        View decor = window.getDecorView();
        decor.setSystemUiVisibility(decor.getSystemUiVisibility()
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    private Screen top() {
        return stack.isEmpty() ? null : stack.get(stack.size() - 1);
    }

    private FrameLayout.LayoutParams full() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public void push(final Screen next) {
        final Screen prev = top();
        stack.add(next);
        final View nv = next.view();
        root.addView(nv, full());
        if (prev != null) {
            prev.onHide();
            int w = root.getWidth() > 0 ? root.getWidth() : getResources().getDisplayMetrics().widthPixels;
            nv.setTranslationX(w);
            nv.animate().translationX(0f).setDuration(220L).withEndAction(new Runnable() {
                @Override
                public void run() {
                    if (top() != prev) {
                        root.removeView(prev.view());
                    }
                }
            });
        }
        next.onShow();
    }

    public void pop() {
        if (stack.size() <= 1) {
            finish();
            return;
        }
        final Screen gone = stack.remove(stack.size() - 1);
        Screen back = top();
        View bv = back.view();
        if (bv.getParent() == null) {
            root.addView(bv, 0, full());
        }
        gone.onHide();
        back.onShow();
        final View gv = gone.view();
        gv.animate().translationX(root.getWidth()).setDuration(200L).withEndAction(new Runnable() {
            @Override
            public void run() {
                root.removeView(gv);
                gone.onDestroy();
            }
        });
    }

    public void replace(Screen next) {
        if (stack.size() > 1) {
            Screen gone = stack.remove(stack.size() - 1);
            gone.onHide();
            root.removeView(gone.view());
            gone.onDestroy();
            Screen back = top();
            if (back.view().getParent() == null) {
                root.addView(back.view(), 0, full());
            }
        }
        push(next);
    }

    @Override
    public void onBackPressed() {
        Screen t = top();
        if (t != null && t.onBack()) {
            return;
        }
        if (stack.size() > 1) {
            pop();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (resumedOnce) {
            Screen t = top();
            if (t != null) {
                t.onShow();
            }
        }
        resumedOnce = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        Screen t = top();
        if (t != null) {
            t.onHide();
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        Screen t = top();
        if (t != null) {
            t.onResult(request, result, data);
        }
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        Screen t = top();
        if (t != null) {
            t.onPermission(request, permissions, results);
        }
    }

    @Override
    protected void onDestroy() {
        for (Screen s : stack) {
            s.onDestroy();
        }
        stack.clear();
        super.onDestroy();
    }
}
