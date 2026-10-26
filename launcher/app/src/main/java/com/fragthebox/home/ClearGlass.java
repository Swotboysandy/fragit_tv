package com.fragthebox.home;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;

/**
 * frag: iOS-style "Clear" glass for the dock, pre-rendered. Live refraction needs Android 13 shaders; this TV runs
 * Android 9. The wallpaper behind the dock doesn't move, so the lens is computed once per wallpaper and dock size:
 * inside a rounded-rect lens the picture is seen as through clear glass, and toward the rim it bends, pulling in
 * the picture from just beyond the edge (the compression ring), with red and blue split a little apart (dispersion).
 * Rendered at half resolution, which the glass hides.
 */
final class ClearGlass {

    private static Bitmap source;      // the shown wallpaper, half size, software
    private static int sourceW, sourceH; // the size the picture is drawn at (before halving)
    private static Bitmap cached;
    private static String cachedKey = "";
    private static int generation;

    private ClearGlass() {
    }

    /** The picture now on screen, as drawn (cover-scaled, dim included); null clears it. */
    static synchronized void setSource(Bitmap halfSize, int drawnW, int drawnH) {
        source = halfSize;
        sourceW = drawnW;
        sourceH = drawnH;
        generation++;
    }

    /**
     * The lens image for a band at {@code band} (window pixels) with corner radius {@code radius}, sized
     * band/2; null when there is no picture to bend.
     */
    static synchronized Bitmap render(RectF band, float radius, int screenW, int screenH, float density) {
        if (source == null || band.width() < 4 || band.height() < 4) return null;
        String key = generation + ":" + Math.round(band.left) + "," + Math.round(band.top) + ","
                + Math.round(band.width()) + "x" + Math.round(band.height()) + ":" + Math.round(radius);
        if (key.equals(cachedKey) && cached != null) return cached;

        int w = Math.max(2, Math.round(band.width() / 2f)), h = Math.max(2, Math.round(band.height() / 2f));
        float r = radius / 2f;
        float edge = 26f * density / 2f;          // width of the bending ring
        float strength = edge * 1.6f;             // how far the rim reaches beyond the edge
        // Where the picture sits on screen: centre-cropped from sourceW x sourceH to the screen.
        float cropX = (sourceW - screenW) / 2f, cropY = (sourceH - screenH) / 2f;
        float sx = source.getWidth() / (float) sourceW, sy = source.getHeight() / (float) sourceH;
        int sw = source.getWidth(), sh = source.getHeight();
        int[] src = new int[sw * sh];
        source.getPixels(src, 0, sw, 0, 0, sw, sh);
        int[] out = new int[w * h];
        float cx = w / 2f, cy = h / 2f, hx = w / 2f - r, hy = h / 2f - r;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float px = x + 0.5f - cx, py = y + 0.5f - cy;
                float d = sdf(px, py, hx, hy, r);
                float depth = -d;
                // Outward normal from the SDF's gradient.
                float nx = sdf(px + 0.5f, py, hx, hy, r) - sdf(px - 0.5f, py, hx, hy, r);
                float ny = sdf(px, py + 0.5f, hx, hy, r) - sdf(px, py - 0.5f, hx, hy, r);
                float nl = (float) Math.sqrt(nx * nx + ny * ny);
                if (nl > 1e-4f) {
                    nx /= nl;
                    ny /= nl;
                }
                float t = Math.max(0f, Math.min(1f, depth / edge));
                float bend = (1f - t) * (1f - t);
                // Screen position of this pixel, then the displaced sample for each colour channel.
                float bx = band.left + (x + 0.5f) * 2f, by = band.top + (y + 0.5f) * 2f;
                float off = bend * strength * 2f;
                int rC = Color.red(sample(src, sw, sh, (bx + nx * off * 1.08f + cropX) * sx, (by + ny * off * 1.08f + cropY) * sy));
                int gC = Color.green(sample(src, sw, sh, (bx + nx * off + cropX) * sx, (by + ny * off + cropY) * sy));
                int bC = Color.blue(sample(src, sw, sh, (bx + nx * off * 0.92f + cropX) * sx, (by + ny * off * 0.92f + cropY) * sy));
                // Clear glass lifts the picture a touch and brightens the ring, as a curved surface catches light.
                float lift = 1.04f + 0.10f * bend;
                out[y * w + x] = Color.rgb(Math.min(255, Math.round(rC * lift + 6)),
                        Math.min(255, Math.round(gC * lift + 6)), Math.min(255, Math.round(bC * lift + 8)));
            }
        }
        if (cached != null) cached.recycle();
        cached = Bitmap.createBitmap(out, w, h, Bitmap.Config.ARGB_8888);
        cachedKey = key;
        return cached;
    }

    /** Signed distance to a rounded rectangle centred at 0 (negative inside). */
    private static float sdf(float px, float py, float hx, float hy, float r) {
        float qx = Math.abs(px) - hx, qy = Math.abs(py) - hy;
        float ox = Math.max(qx, 0f), oy = Math.max(qy, 0f);
        return (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0f) - r;
    }

    private static int sample(int[] src, int w, int h, float x, float y) {
        int ix = Math.max(0, Math.min(w - 1, Math.round(x))), iy = Math.max(0, Math.min(h - 1, Math.round(y)));
        return src[iy * w + ix];
    }
}
