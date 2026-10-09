package com.scut.engine;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ScriptManager {

    private static final String TAG = "SCUT";

    private final Context context;

    public ScriptManager(Context context) {
        this.context = context.getApplicationContext();
    }

    // external dir can be null, so fall back to the internal one
    private File scriptsDir() {
        File base = context.getExternalFilesDir(null);
        if (base == null) {
            base = context.getFilesDir();
        }
        File dir = new File(base, "scripts");
        dir.mkdirs();
        return dir;
    }

    public void ensureExamplesCopied(String[] assetNames) {
        File dir = scriptsDir();
        String[] existing = dir.list();
        if (existing != null && existing.length > 0) {
            return;
        }
        for (String name : assetNames) {
            try {
                copyAsset("scripts/" + name, new File(dir, name));
            } catch (IOException e) {
                Log.w(TAG, "could not copy example " + name, e);
            }
        }
    }

    private void copyAsset(String assetPath, File target) throws IOException {
        InputStream in = context.getAssets().open(assetPath);
        try {
            OutputStream out = new FileOutputStream(target);
            try {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    public List<File> listScripts() {
        File[] found = scriptsDir().listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".lua");
            }
        });
        if (found == null) {
            return Collections.emptyList();
        }
        Arrays.sort(found, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareTo(b.getName());
            }
        });
        return new ArrayList<File>(Arrays.asList(found));
    }

    public String directoryPath() {
        return scriptsDir().getAbsolutePath();
    }
}
