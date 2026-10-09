package com.scut.project;

import android.content.Context;
import android.graphics.Bitmap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class ProjectStore {

    private final File dir;

    public ProjectStore(Context context) {
        dir = new File(context.getFilesDir(), "projects");
        dir.mkdirs();
    }

    public File posterFile(String id) {
        return new File(dir, id + ".jpg");
    }

    public synchronized List<Project> list() {
        List<Project> out = new ArrayList<Project>();
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (!f.getName().endsWith(".json")) {
                    continue;
                }
                try {
                    out.add(parse(new JSONObject(read(f))));
                } catch (Exception ignored) {
                }
            }
        }
        Collections.sort(out, new Comparator<Project>() {
            @Override
            public int compare(Project a, Project b) {
                return a.modified == b.modified ? 0 : (a.modified < b.modified ? 1 : -1);
            }
        });
        return out;
    }

    public String encode(Project p) throws IOException {
        try {
            return toJson(p).toString();
        } catch (JSONException e) {
            throw new IOException("can't write project");
        }
    }

    public void save(Project p) throws IOException {
        write(p.id, encode(p));
    }

    public synchronized void write(String id, String json) throws IOException {
        File tmp = new File(dir, id + ".json.tmp");
        OutputStream out = new FileOutputStream(tmp);
        try {
            out.write(json.getBytes("UTF-8"));
        } finally {
            out.close();
        }
        File target = new File(dir, id + ".json");
        if (!tmp.renameTo(target)) {
            target.delete();
            if (!tmp.renameTo(target)) {
                throw new IOException("can't save project");
            }
        }
    }

    public synchronized void delete(String id) {
        new File(dir, id + ".json").delete();
        new File(dir, id + ".jpg").delete();
    }

    public void savePoster(String id, Bitmap b) {
        try {
            OutputStream out = new FileOutputStream(posterFile(id));
            try {
                b.compress(Bitmap.CompressFormat.JPEG, 85, out);
            } finally {
                out.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static String read(File f) throws IOException {
        InputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
            }
            return out.toString("UTF-8");
        } finally {
            in.close();
        }
    }

    private static JSONObject toJson(Project p) throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", p.id);
        o.put("name", p.name);
        o.put("created", p.created);
        o.put("modified", p.modified);
        o.put("side", p.exportSide);
        o.put("fps", p.exportFps);
        o.put("quality", p.exportQuality);
        JSONArray arr = new JSONArray();
        for (Clip c : p.clips) {
            JSONObject j = new JSONObject();
            j.put("id", c.id);
            j.put("uri", c.uri);
            j.put("name", c.name);
            j.put("source", c.sourceMs);
            j.put("w", c.width);
            j.put("h", c.height);
            j.put("fps", (double) c.fps);
            j.put("in", c.inMs);
            j.put("out", c.outMs);
            j.put("vol", (double) c.volume);
            j.put("audio", c.hasAudio);
            j.put("rate", c.sampleRate);
            j.put("bitrate", c.bitrate);
            j.put("vmime", c.videoMime);
            j.put("amime", c.audioMime);
            j.put("size", c.sizeBytes);
            arr.put(j);
        }
        o.put("clips", arr);
        return o;
    }

    private static Project parse(JSONObject o) throws JSONException {
        Project p = new Project();
        p.id = o.getString("id");
        p.name = o.optString("name", "Project");
        p.created = o.optLong("created");
        p.modified = o.optLong("modified");
        p.exportSide = o.optInt("side", 1080);
        p.exportFps = o.optInt("fps", 30);
        p.exportQuality = o.optInt("quality", 1);
        JSONArray arr = o.optJSONArray("clips");
        if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject j = arr.getJSONObject(i);
                Clip c = new Clip();
                c.id = j.getString("id");
                c.uri = j.getString("uri");
                c.name = j.optString("name", "video");
                c.sourceMs = j.optLong("source");
                c.width = j.optInt("w");
                c.height = j.optInt("h");
                c.fps = (float) j.optDouble("fps", 30);
                c.inMs = j.optLong("in");
                c.outMs = j.optLong("out", c.sourceMs);
                c.volume = (float) j.optDouble("vol", 1);
                c.hasAudio = j.optBoolean("audio");
                c.sampleRate = j.optInt("rate");
                c.bitrate = j.optInt("bitrate");
                c.videoMime = j.optString("vmime", null);
                c.audioMime = j.optString("amime", null);
                c.sizeBytes = j.optLong("size");
                p.clips.add(c);
            }
        }
        return p;
    }
}
