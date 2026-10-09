package com.scut.media;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.ContentUris;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public final class MediaLibrary {

    public static final class Item {
        public long id;
        public Uri uri;
        public String name;
        public long durationMs;
        public long sizeBytes;
        public int width;
        public int height;
    }

    private MediaLibrary() {
    }

    public static String permission() {
        return Build.VERSION.SDK_INT >= 33
                ? "android.permission.READ_MEDIA_VIDEO"
                : "android.permission.READ_EXTERNAL_STORAGE";
    }

    public static boolean hasPermission(Activity a) {
        return a.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED;
    }

    public static List<Item> queryVideos(Context ctx) {
        List<Item> out = new ArrayList<Item>();
        String[] cols = {
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
        };
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cols,
                    null, null, MediaStore.Video.Media.DATE_ADDED + " DESC");
            while (c != null && c.moveToNext()) {
                Item it = new Item();
                it.id = c.getLong(0);
                it.name = c.getString(1);
                it.durationMs = c.getLong(2);
                it.sizeBytes = c.getLong(3);
                it.width = c.getInt(4);
                it.height = c.getInt(5);
                if (it.durationMs <= 0) {
                    continue;
                }
                it.uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, it.id);
                out.add(it);
            }
        } catch (RuntimeException ignored) {
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return out;
    }

    public static Uri saveToGallery(Context ctx, File file, String name) throws IOException {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = ctx.getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            v.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            v.put("relative_path", "Movies/SCUT");
            v.put("is_pending", 1);
            Uri uri = cr.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
            if (uri == null) {
                throw new IOException("gallery said no");
            }
            try {
                OutputStream out = cr.openOutputStream(uri);
                if (out == null) {
                    throw new IOException("gallery said no");
                }
                copy(file, out);
            } catch (IOException e) {
                cr.delete(uri, null, null);
                throw e;
            }
            ContentValues done = new ContentValues();
            done.put("is_pending", 0);
            cr.update(uri, done, null, null);
            return uri;
        }
        File dir = new File(ctx.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "SCUT");
        dir.mkdirs();
        File target = new File(dir, name);
        copy(file, new java.io.FileOutputStream(target));
        return Uri.fromFile(target);
    }

    private static void copy(File from, OutputStream out) throws IOException {
        InputStream in = new FileInputStream(from);
        try {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
        } finally {
            in.close();
            out.close();
        }
    }
}
