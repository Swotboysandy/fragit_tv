package com.fragthebox.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

/**
 * One soft pill behind whatever has focus inside a container, gliding to the next item on springs instead of
 * each item lighting up on its own (the segmented-control look). Its four edges spring separately, so it
 * stretches a little as it travels and settles without overshoot.
 */
final class SlidingPill extends View {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float density;
    private final float inset;
    private final float radius;   // < 0: a full pill (half the height)
    private final SpringAnimation[] springs = new SpringAnimation[4];
    private final float[] edges = new float[4];
    private ViewGroup content;
    private boolean placed;
    private ViewTreeObserver.OnGlobalFocusChangeListener focusListener;

    SlidingPill(Context ctx, float insetDp, float radiusDp) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        inset = insetDp * density;
        radius = radiusDp < 0 ? -1f : radiusDp * density;
        fill.setColor(0xF5FFFFFF);   // pill_bg / input_row_bg focused: near-white
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1f, 0.8f * density));
        for (int i = 0; i < 4; i++) {
            final int edge = i;
            springs[i] = new SpringAnimation(this, new FloatPropertyCompat<SlidingPill>("edge" + i) {
                @Override
                public float getValue(SlidingPill p) {
                    return p.edges[edge];
                }

                @Override
                public void setValue(SlidingPill p, float v) {
                    p.edges[edge] = v;
                    p.invalidate();
                }
            });
            // The leading edge is a touch stiffer than the trailing one, which gives the glide its stretch.
            springs[i].setSpring(new SpringForce().setStiffness(i % 2 == 0 ? 520f : 440f).setDampingRatio(0.9f));
            springs[i].setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_PIXELS);
        }
        setAlpha(0f);
    }

    /** Follow focus among the descendants of {@code content}; this pill must share its parent's coordinates. */
    void follow(ViewGroup content) {
        this.content = content;
    }

    /**
     * Takes its parent's size, never sets it: in a wrap_content sheet a full-size pill would stretch the sheet to
     * the whole screen. FrameLayout measures match_parent children again at its final size, so this still fills it.
     */
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(
                MeasureSpec.getMode(widthSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(widthSpec) : 0,
                MeasureSpec.getMode(heightSpec) == MeasureSpec.EXACTLY ? MeasureSpec.getSize(heightSpec) : 0);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        focusListener = (oldFocus, newFocus) -> {
            if (content != null && newFocus != null && isInside(newFocus)) {
                moveTo(newFocus, true);
                animate().alpha(1f).setDuration(160).start();
            } else if (content != null && oldFocus != null && isInside(oldFocus)) {
                onLeave();
            }
        };
        getViewTreeObserver().addOnGlobalFocusChangeListener(focusListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalFocusChangeListener(focusListener);
        super.onDetachedFromWindow();
    }

    /** Where the pill rests when focus leaves the container; by default it fades away. */
    private View rest;

    void restOn(View v) {
        rest = v;
        // Placed once the bar has its size, and again if it is laid out anew while focus is elsewhere.
        v.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (r > l && !isInside(focusedInWindow())) {
                moveTo(rest, false);
                setAlpha(0.16f);
            }
        });
    }

    private void onLeave() {
        if (rest != null) {
            moveTo(rest, true);
            animate().alpha(0.16f).setDuration(200).start();
        } else {
            animate().alpha(0f).setDuration(160).start();
        }
    }

    private View focusedInWindow() {
        return getRootView() == null ? null : getRootView().findFocus();
    }

    private boolean isInside(View v) {
        for (View p = v; p != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == content) return true;
        }
        return false;
    }

    private void moveTo(View target, boolean animate) {
        if (target.getWidth() == 0 || !target.isLaidOut()) {
            // Focus can land on an item before its sheet is laid out (the first item as a sheet opens):
            // place the pill once the item has a size, without a glide from nowhere.
            target.addOnLayoutChangeListener(new OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    v.removeOnLayoutChangeListener(this);
                    if (r > l) moveTo(v, false);
                }
            });
            return;
        }
        Rect r = new Rect();
        target.getDrawingRect(r);
        ((ViewGroup) getParent()).offsetDescendantRectToMyCoords(target, r);
        float[] to = {r.left + inset, r.top + inset, r.right - inset, r.bottom - inset};
        for (int i = 0; i < 4; i++) {
            if (animate && placed) {
                springs[i].animateToFinalPosition(to[i]);
            } else {
                springs[i].cancel();
                edges[i] = to[i];
            }
        }
        placed = true;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!placed) return;
        rect.set(edges[0], edges[1], edges[2], edges[3]);
        float rr = radius < 0 ? rect.height() / 2f : radius;
        canvas.drawRoundRect(rect, rr, rr, fill);
    }
}
