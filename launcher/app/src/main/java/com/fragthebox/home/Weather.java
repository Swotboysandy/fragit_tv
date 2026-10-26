package com.fragthebox.home;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * frag: the weather line under the date, "24° · CLEAR · PATNA". The place comes from the network's public IP
 * (ipwho.is, no key), the weather from Open-Meteo (no key). Fetched at most every 30 minutes and cached, so a
 * restart or a moment offline still shows the last reading; nothing is sent but the requests themselves.
 */
final class Weather {

    interface Callback {
        void onWeather(String line);
    }

    private static final String PREFS = "frag_weather";
    private static final long MAX_AGE = 30 * 60 * 1000L;

    private Weather() {
    }

    /** The cached line now (if any), then a fresh one when the cache is older than 30 minutes. */
    static void load(Context ctx, Callback cb) {
        Context app = ctx.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        SharedPreferences p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String cached = p.getString("line", "");
        if (!cached.isEmpty()) cb.onWeather(cached);
        if (System.currentTimeMillis() - p.getLong("at", 0) < MAX_AGE) return;
        new Thread(() -> {
            try {
                JSONObject place = new JSONObject(get("https://ipwho.is/?fields=success,city,latitude,longitude"));
                if (!place.optBoolean("success")) return;
                double lat = place.getDouble("latitude"), lon = place.getDouble("longitude");
                JSONObject cur = new JSONObject(get(String.format(Locale.ROOT,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.3f&longitude=%.3f&current=temperature_2m,weather_code",
                        lat, lon))).getJSONObject("current");
                String line = Math.round(cur.getDouble("temperature_2m")) + "°  ·  " + describe(cur.getInt("weather_code"))
                        + "  ·  " + place.optString("city", "");
                p.edit().putString("line", line).putLong("at", System.currentTimeMillis()).apply();
                main.post(() -> cb.onWeather(line));
            } catch (Exception ignored) {
                // offline or rate limited: keep the cached line
            }
        }, "frag-weather").start();
    }

    /** WMO weather codes, as Open-Meteo reports them, in a word or two. */
    static String describe(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Showers";
        if (code >= 85 && code <= 86) return "Snow showers";
        if (code >= 95) return "Thunderstorm";
        return "";
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "frag_the_box");
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
