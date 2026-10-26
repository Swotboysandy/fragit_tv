package com.fragthebox.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.widget.FrameLayout;

/**
 * The classic launcher's glass, value for value:
 * - {@link #PILL}: pill_bg's resting state, white 18% fading to 8% top to bottom, a 1 dp rim of white at 22%;
 *   used for the bar, as for the Inputs, Wi-Fi and Settings pills.
 * - {@link #PANEL}: glass_panel, the Inputs popup's dark slab, #F22A2A2E to #F5161618 with a 1 dp #33FFFFFF rim.
 * - {@link #MATTE}: the band behind the row, the same glass a little milkier (white 25% to 15%, rim 30%), so it
 *   reads as one frosted shelf the tiles sit in.
 */
final class GlassPanel extends FrameLayout {

    static final int PILL = 0, PANEL = 1, MATTE = 2;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final float radius;   // < 0: fully round ends
    private final int style;

    GlassPanel(Context ctx, float radiusDp, int style) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        radius = radiusDp < 0 ? -1f : radiusDp * density;
        this.style = style;
        setWillNotDraw(false);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(density);
        rim.setColor(style == PILL ? 0x38FFFFFF : style == MATTE ? 0x4DFFFFFF : 0x33FFFFFF);
        setElevation(style == PANEL ? 12 * density : 0);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fill.setShader(style == PILL
                ? new LinearGradient(0, 0, 0, h, 0x2EFFFFFF, 0x14FFFFFF, Shader.TileMode.CLAMP)
                : style == MATTE
                ? new LinearGradient(0, 0, 0, h, 0x40FFFFFF, 0x26FFFFFF, Shader.TileMode.CLAMP)
                : new LinearGradient(0, 0, 0, h, 0xF22A2A2E, 0xF5161618, Shader.TileMode.CLAMP));
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(android.view.View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r(view.getHeight()));
            }
        });
    }

    private float r(int h) {
        return radius < 0 ? h / 2f : radius;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float half = rim.getStrokeWidth() / 2f;
        rect.set(half, half, getWidth() - half, getHeight() - half);
        float rr = r(getHeight());
        canvas.drawRoundRect(rect, rr, rr, fill);
        canvas.drawRoundRect(rect, rr - half, rr - half, rim);
    }
}
