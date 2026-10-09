package com.scut.ui;

import android.content.Intent;
import android.view.View;

public interface Screen {

    View view();

    void onShow();

    void onHide();

    boolean onBack();

    void onDestroy();

    void onResult(int request, int result, Intent data);

    void onPermission(int request, String[] permissions, int[] results);
}
