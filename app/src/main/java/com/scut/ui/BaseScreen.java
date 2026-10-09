package com.scut.ui;

import android.content.Intent;

public abstract class BaseScreen implements Screen {

    @Override
    public void onShow() {
    }

    @Override
    public void onHide() {
    }

    @Override
    public boolean onBack() {
        return false;
    }

    @Override
    public void onDestroy() {
    }

    @Override
    public void onResult(int request, int result, Intent data) {
    }

    @Override
    public void onPermission(int request, String[] permissions, int[] results) {
    }
}
