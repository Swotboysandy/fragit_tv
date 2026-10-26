package com.fragthebox.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.FrameLayout;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Frosted glass: the wallpaper behind the panel, blurred, under a light tint, a sheen across the top and a
 * bright hairline rim. Android 9 has no live blur (RenderEffect is Android 12), so the console blurs the
 * wallpaper once when it changes ({@link #setBackdrop}) and every panel draws its own slice of that copy,
 * following the panel as it moves on its springs.
 */
final class GlassPanel extends FrameLayout {

    private static Bitmap backdrop;
    private static int screenW, screenH;
    private static final Set<GlassPanel> LIVE = Collections.newSetFromMap(new WeakHashMap<>());

    private final Paint frost = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix matrix = new Matrix();
    private final RectF rect = new RectF();
    private final int[] loc = new int[2];
    private final float density;
    private float radius;
    private int tintColor;

    GlassPanel(Context ctx, float radiusDp, int tintColor) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        radius = radiusDp * density;
        this.tintColor = tintColor;
        setWillNotDraw(false);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, 0.8f * density));
        LIVE.add(this);
    }

    /** A blurred copy of the wallpaper as it fills the screen; every panel redraws from it. */
    static void setBackdrop(Bitmap wallpaper, int width, int height) {
        screenW = width;
        screenH = height;
        backdrop = blur(wallpaper, width, height);
        for (GlassPanel p : LIVE) p.invalidate();
    }

    /** Centre-crop to the screen, then two shrink-and-grow passes: a soft, even frost at almost no cost. */
    private static Bitmap blur(Bitmap src, int w, int h) {
        if (src == null || w <= 0 || h <= 0) return null;
        float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        int cw = Math.round(w / scale), ch = Math.round(h / scale);
        int cx = (src.getWidth() - cw) / 2, cy = (src.getHeight() - ch) / 2;
        Bitmap crop = Bitmap.createBitmap(src, Math.max(0, cx), Math.max(0, cy),
                Math.min(cw, src.getWidth()), Math.min(ch, src.getHeight()));
        Bitmap tiny = Bitmap.createScaledBitmap(crop, Math.max(1, w / 40), Math.max(1, h / 40), true);
        Bitmap mid = Bitmap.createScaledBitmap(tiny, Math.max(1, w / 12), Math.max(1, h / 12), true);
        Bitmap tiny2 = Bitmap.createScaledBitmap(mid, Math.max(1, w / 32), Math.max(1, h / 32), true);
        Bitmap out = Bitmap.createScaledBitmap(tiny2, Math.max(1, w / 4), Math.max(1, h / 4), true);
        if (crop != src) crop.recycle();
        tiny.recycle();
        mid.recycle();
        tiny2.recycle();
        return out;
    }

    void setRadius(float dp) {
        radius = dp * density;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        sheen.setShader(new LinearGradient(0, 0, 0, h * 0.55f, 0x26FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
        rim.setShader(new LinearGradient(0, 0, 0, h, 0x73FFFFFF, 0x1FFFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        rect.set(0, 0, getWidth(), getHeight());
        Bitmap b = backdrop;
        if (b != null && !b.isRecycled()) {
            getLocationOnScreen(loc);
            BitmapShader s = new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            matrix.setScale(screenW / (float) b.getWidth(), screenH / (float) b.getHeight());
            matrix.postTranslate(-loc[0], -loc[1]);
            s.setLocalMatrix(matrix);
            frost.setShader(s);
            canvas.drawRoundRect(rect, radius, radius, frost);
        }
        tint.setColor(tintColor);
        canvas.drawRoundRect(rect, radius, radius, tint);
        canvas.drawRoundRect(rect, radius, radius, sheen);
        float half = rim.getStrokeWidth() / 2f;
        rect.inset(half, half);
        canvas.drawRoundRect(rect, radius - half, radius - half, rim);
    }
}
