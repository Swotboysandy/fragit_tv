package com.fragthebox.home;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * frag brightness: Auto or a fixed level, kept in preferences and applied by {@link BrightnessService} as a dim
 * layer over everything. This TV's panel backlight is not reachable by apps, so the layer darkens the picture;
 * the backlight itself stays in the TV's own Picture settings.
 *
 * Auto follows the time of day (TVs have no light sensor): full by day, softer and a little warmer in the
 * evening, dimmest and warmest late at night.
 */
final class Brightness {

    static final int AUTO = -1;
    private static final String PREFS = "frag_brightness", KEY = "level";

    private Brightness() {
    }

    /** AUTO or 10..100 (percent). */
    static int level(Context ctx) {
        return prefs(ctx).getInt(KEY, 100);
    }

    static void set(Context ctx, int level) {
        prefs(ctx).edit().putInt(KEY, level).apply();
    }

    /** What the layer should show now: {brightness 0..1, warmth 0..1}. */
    static float[] now(Context ctx) {
        int level = level(ctx);
        if (level != AUTO) return new float[]{level / 100f, 0f};
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (h >= 7 && h < 18) return new float[]{1f, 0f};
        if (h >= 18 && h < 22) return new float[]{0.8f, 0.12f};
        return new float[]{0.55f, 0.28f};
    }

    /** True when the layer has nothing to do (full brightness, no warmth). */
    static boolean idle(Context ctx) {
        float[] n = now(ctx);
        return n[0] >= 0.999f && n[1] <= 0.001f && level(ctx) != AUTO;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
