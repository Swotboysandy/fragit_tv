package com.fragthebox.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Wallpapers from a folder of a public GitHub repository (config.xml wallpaper_repo / wallpaper_path).
 * The folder listing comes from the contents API at most once per rotation, the picture shown is cached in
 * the app's files, and the TV keeps showing the last one when it is offline. Add or remove pictures in the
 * repo and the TV picks them up at the next rotation.
 */
final class GitWallpapers {

    interface Callback {
        void onWallpaper(Bitmap bitmap);
    }

    interface ListCallback {
        /** Every picture's full-size URL, with a thumbnail URL from thumbs/ (null when the folder has none). */
        void onList(List<String> urls, List<String> thumbs, String pinned);
    }

    private static final String PREFS = "git_wallpapers";
    private static final long LIST_MAX_AGE = 60 * 60 * 1000L;

    private final Context ctx;
    private final String repo, path;
    private final long rotateMs;
    private final int width, height;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final File current;

    GitWallpapers(Context ctx, String repo, String path, long rotateMs, int width, int height) {
        this.ctx = ctx.getApplicationContext();
        this.repo = repo;
        this.path = path;
        this.rotateMs = rotateMs;
        this.width = width;
        this.height = height;
        this.current = new File(this.ctx.getFilesDir(), "git_wallpaper");
    }

    /** The cached picture now (if any), then a new one when the rotation is due or a picked one isn't shown yet. */
    void load(Callback cb) {
        new Thread(() -> {
            Bitmap cached = decode(current);
            if (cached != null) post(cb, cached);
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String pinned = p.getString("pinned", "");
            boolean due = pinned.isEmpty()
                    ? cached == null || System.currentTimeMillis() - p.getLong("shown_at", 0) >= rotateMs
                    : cached == null || !pinned.equals(p.getString("shown_url", ""));
            if (!due || repo.isEmpty()) return;
            try {
                List<String> urls = listing(p);
                if (urls.isEmpty()) return;
                int next = pinned.isEmpty() ? (p.getInt("index", -1) + 1) % urls.size() : urls.indexOf(pinned);
                if (next < 0) next = 0;
                File tmp = new File(ctx.getFilesDir(), "git_wallpaper.part");
                download(urls.get(next), tmp);
                Bitmap fresh = decode(tmp);
                if (fresh == null) return;
                if (!tmp.renameTo(current)) {
                    current.delete();
                    if (!tmp.renameTo(current)) return;
                }
                p.edit().putInt("index", next).putLong("shown_at", System.currentTimeMillis())
                        .putString("shown_url", urls.get(next)).apply();
                post(cb, fresh);
            } catch (Exception ignored) {
                // offline, rate limited or a bad file: keep what is on screen
            }
        }, "git-wallpapers").start();
    }

    /** The pictures in the folder, for the picker; on the main thread. */
    void list(ListCallback cb) {
        new Thread(() -> {
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            List<String> urls = new ArrayList<>(), thumbs = new ArrayList<>();
            try {
                urls = listing(p);
                for (String u : urls) {
                    int slash = u.lastIndexOf('/');
                    thumbs.add(u.substring(0, slash) + "/thumbs" + u.substring(slash));
                }
            } catch (Exception ignored) {
                // offline: an empty picker says so
            }
            List<String> fu = urls, ft = thumbs;
            String pinned = p.getString("pinned", "");
            main.post(() -> cb.onList(fu, ft, pinned));
        }, "git-wallpapers-list").start();
    }

    /** Shows this picture from now on; null goes back to rotating through the folder. */
    void pin(String url, Callback cb) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        p.edit().putString("pinned", url == null ? "" : url).putLong("shown_at", 0).apply();
        load(cb);
    }

    /** A small picture for the picker, decoded off the main thread; null when it can't be fetched. */
    static Bitmap thumb(String url) {
        try {
            byte[] b = fetch(url);
            return BitmapFactory.decodeByteArray(b, 0, b.length);
        } catch (Exception e) {
            return null;
        }
    }

    private List<String> listing(SharedPreferences p) throws Exception {
        String saved = p.getString("listing", "");
        long at = p.getLong("listing_at", 0);
        if (!saved.isEmpty() && System.currentTimeMillis() - at < Math.min(rotateMs, LIST_MAX_AGE)) {
            return parse(saved);
        }
        String api = "https://api.github.com/repos/" + repo + "/contents/" + path;
        String body = new String(fetch(api), "UTF-8");
        List<String> urls = parse(body);
        p.edit().putString("listing", body).putLong("listing_at", System.currentTimeMillis()).apply();
        return urls;
    }

    private static List<String> parse(String json) throws Exception {
        List<String> urls = new ArrayList<>();
        JSONArray arr = new JSONArray(json);
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.getJSONObject(i);
            String name = o.optString("name").toLowerCase(Locale.ROOT);
            String url = o.optString("download_url");
            if (!"file".equals(o.optString("type")) || url.isEmpty()) continue;
            if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".webp")) {
                urls.add(url);
            }
        }
        Collections.sort(urls);
        return urls;
    }

    private static byte[] fetch(String url) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            copy(in, out);
            return out.toByteArray();
        } finally {
            c.disconnect();
        }
    }

    private static void download(String url, File to) throws IOException {
        HttpURLConnection c = open(url);
        try (InputStream in = c.getInputStream(); OutputStream out = new FileOutputStream(to)) {
            copy(in, out);
        } finally {
            c.disconnect();
        }
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", "frag_the_box-launcher");
        c.setRequestProperty("Accept", "application/vnd.github+json");
        if (c.getResponseCode() != 200) throw new IOException("HTTP " + c.getResponseCode());
        return c;
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[16384];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }

    /** Decoded no larger than the screen and as RGB_565: about 4 MB at 1080p on a 1 GB TV. */
    private Bitmap decode(File f) {
        if (!f.isFile()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= width && o.outHeight / (sample * 2) >= height) sample *= 2;
        BitmapFactory.Options d = new BitmapFactory.Options();
        d.inSampleSize = sample;
        d.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), d);
    }

    private void post(Callback cb, Bitmap b) {
        main.post(() -> cb.onWallpaper(b));
    }
}
