package com.fragthebox.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;

/**
 * Card art for the console row, drawn once off the main thread into one bitmap per app:
 * - a TV banner sits inset with even padding and its own rounded corners, over a soft blurred fill made
 *   from the banner itself, so a 16:9 banner reads as one piece of art on a near-square card;
 * - a phone app with only a launcher icon gets a card tinted from the icon, the icon centred and its name
 *   under it, instead of a stretched or letterboxed icon.
 */
final class CardArt {

    private CardArt() {
    }

    static Bitmap banner(Context ctx, Drawable banner, int w, int h) {
        float d = ctx.getResources().getDisplayMetrics().density;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Edge edge = edge(banner);

        if (edge.transparent || edge.plain) {
            // Plain edge (Netflix, Prime Video, YouTube) or a transparent banner (VLC): the card takes the edge
            // colour and the banner runs across it seamlessly, so the whole card is the app's art.
            // Flat under the banner so no seam shows; the depth comes from one shade over the whole card.
            c.drawColor(edge.transparent ? 0xFF2A2F3A : edge.color);
            int bw = edge.transparent ? Math.round(w * 0.78f) : w;
            int bh = Math.round(bw * 9f / 16f);
            Bitmap art = render(banner, bw, bh);
            c.drawBitmap(art, (w - bw) / 2f, (h - bh) / 2f, new Paint(Paint.FILTER_BITMAP_FLAG));
            art.recycle();
            fill(c, w, h, 0x0AFFFFFF, 0x1F000000);
            return out;
        }

        // A picture banner (artwork to its edges): a dark card tinted from it, the banner inset in a frame.
        Bitmap art = render(banner, Math.round(w - 2 * 9 * d), Math.round((w - 2 * 9 * d) * 9f / 16f));
        fill(c, w, h, darken(edge.color, 0.55f), darken(edge.color, 0.78f));

        // The banner itself, inset 9 dp, rounded 9 dp, with a faint shadow under it.
        float left = (w - art.getWidth()) / 2f, top = (h - art.getHeight()) / 2f;
        RectF r = new RectF(left, top, left + art.getWidth(), top + art.getHeight());
        Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadow.setColor(0x40000000);
        c.drawRoundRect(new RectF(r.left, r.top + 2 * d, r.right, r.bottom + 3 * d), 9 * d, 9 * d, shadow);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        BitmapShader shader = new BitmapShader(art, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setTranslate(left, top);
        shader.setLocalMatrix(m);
        p.setShader(shader);
        c.drawRoundRect(r, 9 * d, 9 * d, p);
        Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, 0.6f * d));
        rim.setColor(0x1FFFFFFF);
        c.drawRoundRect(r, 9 * d, 9 * d, rim);
        art.recycle();
        return out;
    }

    static Bitmap icon(Context ctx, Drawable icon, String label, int w, int h) {
        float d = ctx.getResources().getDisplayMetrics().density;
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        int size = Math.round(52 * d);
        Bitmap ic = render(icon, size, size);
        int tint = average(ic);
        float[] hsv = new float[3];
        Color.colorToHSV(tint, hsv);
        hsv[1] = Math.min(hsv[1], 0.55f);
        int top = Color.HSVToColor(new float[]{hsv[0], hsv[1], 0.42f});
        int bottom = Color.HSVToColor(new float[]{hsv[0], hsv[1], 0.22f});
        Paint bg = new Paint();
        bg.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, bg);

        float iy = h * 0.40f - size / 2f;
        Paint glow = new Paint(Paint.ANTI_ALIAS_FLAG);
        glow.setColor(0x26000000);
        c.drawCircle(w / 2f, iy + size / 2f + 2 * d, size * 0.52f, glow);
        c.drawBitmap(ic, (w - size) / 2f, iy, new Paint(Paint.FILTER_BITMAP_FLAG));
        ic.recycle();

        TextPaint tp = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        tp.setColor(0xF2FFFFFF);
        tp.setTextSize(11 * ctx.getResources().getDisplayMetrics().scaledDensity);
        tp.setTypeface(ctx.getResources().getFont(R.font.outfit_medium));
        tp.setTextAlign(Paint.Align.CENTER);
        CharSequence name = TextUtils.ellipsize(label, tp, w - 20 * d, TextUtils.TruncateAt.END);
        c.drawText(name, 0, name.length(), w / 2f, h * 0.80f, tp);
        return out;
    }

    /**
     * The app's own colour for the ambient glow: the most vivid colour in its art (Netflix red, Prime blue),
     * not the edge, which is often white. Grey art falls back to a cool slate.
     */
    static int accent(Drawable art) {
        Bitmap b = render(art, 48, 27);
        float best = 0f;
        int color = 0xFF5A6B8C;
        float[] hsv = new float[3];
        for (int y = 0; y < 27; y++) {
            for (int x = 0; x < 48; x++) {
                int p = b.getPixel(x, y);
                if (Color.alpha(p) < 160) continue;
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * hsv[2];
                if (score > best) {
                    best = score;
                    color = p | 0xFF000000;
                }
            }
        }
        b.recycle();
        return best < 0.18f ? 0xFF5A6B8C : color;
    }

    private static final class Edge {
        int color;
        boolean plain, transparent;
    }

    /** The banner's border: its mean colour, whether it is one flat colour, and whether it is see-through. */
    private static Edge edge(Drawable banner) {
        Bitmap b = render(banner, 64, 36);
        long r = 0, g = 0, bl = 0, a = 0;
        int n = 0;
        int[] px = new int[64 * 2 + 34 * 2];
        for (int x = 0; x < 64; x++) {
            px[n++] = b.getPixel(x, 0);
            px[n++] = b.getPixel(x, 35);
        }
        for (int y = 1; y < 35; y++) {
            px[n++] = b.getPixel(0, y);
            px[n++] = b.getPixel(63, y);
        }
        b.recycle();
        for (int p : px) {
            r += Color.red(p);
            g += Color.green(p);
            bl += Color.blue(p);
            a += Color.alpha(p);
        }
        Edge e = new Edge();
        int mr = (int) (r / n), mg = (int) (g / n), mb = (int) (bl / n);
        e.color = Color.rgb(mr, mg, mb);
        e.transparent = a / n < 200;
        double var = 0;
        for (int p : px) {
            int dr = Color.red(p) - mr, dg = Color.green(p) - mg, db = Color.blue(p) - mb;
            var += dr * dr + dg * dg + db * db;
        }
        e.plain = Math.sqrt(var / n) < 22;
        return e;
    }

    private static void fill(Canvas c, int w, int h, int top, int bottom) {
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
    }

    private static int lighten(int color, float f) {
        return Color.rgb(Math.round(Color.red(color) + (255 - Color.red(color)) * f),
                Math.round(Color.green(color) + (255 - Color.green(color)) * f),
                Math.round(Color.blue(color) + (255 - Color.blue(color)) * f));
    }

    private static int darken(int color, float f) {
        return Color.rgb(Math.round(Color.red(color) * (1 - f)), Math.round(Color.green(color) * (1 - f)),
                Math.round(Color.blue(color) * (1 - f)));
    }

    private static Bitmap render(Drawable dr, int w, int h) {
        Bitmap b = Bitmap.createBitmap(Math.max(1, w), Math.max(1, h), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        dr.setBounds(0, 0, w, h);
        dr.draw(c);
        return b;
    }

    private static int average(Bitmap b) {
        Bitmap one = Bitmap.createScaledBitmap(b, 1, 1, true);
        int px = one.getPixel(0, 0);
        one.recycle();
        return Color.alpha(px) < 40 ? 0xFF3A4150 : px | 0xFF000000;
    }
}
