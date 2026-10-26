package com.fragthebox.home;

import android.graphics.Bitmap;
import android.graphics.Color;

/**
 * Reads a wallpaper's light: where the light comes from (the brightest soft area), its colour, and the
 * picture's overall mood colour. The console paints its glow, shade and glass from these instead of fixed
 * colours, so the interface sits in the picture's own light rather than on top of it.
 */
final class Ambience {

    /** Light source position, 0..1 across and down the screen. */
    float lightX = 0.5f, lightY = 0.3f;
    int lightColor = 0xFFFFF4E0;
    /** The picture's average colour, for tinting glass. */
    int moodColor = 0xFF5A6B8C;
    /** The darkest band of the picture's bottom, for the shade under the row (never pure black). */
    int floorColor = 0xFF0B0D12;
    /** 0 (night) .. 1 (bright daylight): how much the interface should lean on light rather than dark. */
    float brightness = 0.3f;

    static Ambience of(Bitmap wall) {
        Ambience a = new Ambience();
        if (wall == null) return a;
        final int gw = 32, gh = 18;
        Bitmap g = Bitmap.createScaledBitmap(wall, gw, gh, true);
        float[] lum = new float[gw * gh];
        long r = 0, gr = 0, b = 0, br = 0, bg = 0, bb = 0;
        int bn = 0;
        float total = 0;
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                int p = g.getPixel(x, y);
                float l = (0.2126f * Color.red(p) + 0.7152f * Color.green(p) + 0.0722f * Color.blue(p)) / 255f;
                lum[y * gw + x] = l;
                total += l;
                r += Color.red(p);
                gr += Color.green(p);
                b += Color.blue(p);
                if (y >= gh * 3 / 4) {
                    br += Color.red(p);
                    bg += Color.green(p);
                    bb += Color.blue(p);
                    bn++;
                }
            }
        }
        int n = gw * gh;
        a.moodColor = Color.rgb((int) (r / n), (int) (gr / n), (int) (b / n));
        a.brightness = Math.min(1f, total / n / 0.6f);
        a.floorColor = darken(Color.rgb((int) (br / bn), (int) (bg / bn), (int) (bb / bn)), 0.7f);

        // The light: centroid of the brightest tenth of the picture, weighted by how bright it is.
        float[] sorted = lum.clone();
        java.util.Arrays.sort(sorted);
        float cut = sorted[(int) (n * 0.9f)];
        float sx = 0, sy = 0, sw = 0;
        long lr = 0, lg = 0, lb = 0;
        int ln = 0;
        for (int y = 0; y < gh; y++) {
            for (int x = 0; x < gw; x++) {
                float l = lum[y * gw + x];
                if (l < cut) continue;
                float w = l * l;
                sx += (x + 0.5f) * w;
                sy += (y + 0.5f) * w;
                sw += w;
                int p = g.getPixel(x, y);
                lr += Color.red(p);
                lg += Color.green(p);
                lb += Color.blue(p);
                ln++;
            }
        }
        g.recycle();
        if (sw > 0) {
            a.lightX = sx / sw / gw;
            a.lightY = sy / sw / gh;
            // The light's own hue, pushed toward white the way light reads to the eye.
            int c = Color.rgb((int) (lr / ln), (int) (lg / ln), (int) (lb / ln));
            a.lightColor = mix(c, 0xFFFFFFFF, 0.35f);
        }
        return a;
    }

    static int mix(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    static int darken(int c, float f) {
        return Color.rgb(Math.round(Color.red(c) * (1 - f)), Math.round(Color.green(c) * (1 - f)),
                Math.round(Color.blue(c) * (1 - f)));
    }

    static int withAlpha(int c, int alpha) {
        return (c & 0x00FFFFFF) | (alpha << 24);
    }
}
