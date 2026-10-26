package com.fragthebox.home;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.tv.TvContract;
import android.net.Uri;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * The newest unfinished row any app published to Android TV's Continue watching (Watch Next), for the
 * console row's first card. Needs READ_TV_LISTINGS, granted by the installer with pm grant; without it, or
 * with no rows, there is simply no continue card. See docs/watch-next.md for what apps publish.
 */
final class ContinueWatching {

    static final class Entry {
        String title, pkg;
        float progress;
        Intent intent;
        Bitmap poster;
    }

    static final String READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS";
    private static final long MAX_AGE = 30L * 24 * 60 * 60 * 1000;

    private ContinueWatching() {
    }

    /** Off the main thread: it reads the TV provider and may fetch the poster. */
    static Entry latest(Context ctx) {
        if (ctx.checkSelfPermission(READ_TV_LISTINGS) != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Entry best = null;
        long bestAt = 0;
        long now = System.currentTimeMillis();
        // No selection: a caller without ACCESS_ALL_EPG_DATA gets a SecurityException when it passes one.
        try (Cursor c = ctx.getContentResolver().query(TvContract.WatchNextPrograms.CONTENT_URI, null, null, null, null)) {
            if (c == null) return null;
            int cTitle = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_TITLE);
            int cPkg = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_PACKAGE_NAME);
            int cPoster = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_POSTER_ART_URI);
            int cIntent = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_INTENT_URI);
            int cAt = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS);
            int cPos = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS);
            int cDur = c.getColumnIndex(TvContract.WatchNextPrograms.COLUMN_DURATION_MILLIS);
            while (c.moveToNext()) {
                long at = cAt >= 0 ? c.getLong(cAt) : 0;
                long pos = cPos >= 0 ? c.getLong(cPos) : 0;
                long dur = cDur >= 0 ? c.getLong(cDur) : 0;
                if (at < now - MAX_AGE || at <= bestAt || dur <= 0) continue;
                float progress = pos / (float) dur;
                if (progress <= 0.01f || progress >= 0.95f) continue;
                String intentUri = cIntent >= 0 ? c.getString(cIntent) : null;
                if (intentUri == null) continue;
                Entry e = new Entry();
                try {
                    e.intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME);
                } catch (Exception bad) {
                    continue;
                }
                e.title = cTitle >= 0 ? c.getString(cTitle) : "";
                e.pkg = cPkg >= 0 ? c.getString(cPkg) : "";
                e.progress = progress;
                String poster = cPoster >= 0 ? c.getString(cPoster) : null;
                e.poster = poster == null ? null : loadPoster(ctx, poster);
                best = e;
                bestAt = at;
            }
        } catch (RuntimeException e) {
            return null;
        }
        return best;
    }

    private static Bitmap loadPoster(Context ctx, String uri) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        try {
            if (uri.startsWith("http")) {
                HttpURLConnection c = (HttpURLConnection) new URL(uri).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(15000);
                try (InputStream in = c.getInputStream()) {
                    return BitmapFactory.decodeStream(in, null, o);
                } finally {
                    c.disconnect();
                }
            }
            try (InputStream in = ctx.getContentResolver().openInputStream(Uri.parse(uri))) {
                return in == null ? null : BitmapFactory.decodeStream(in, null, o);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
