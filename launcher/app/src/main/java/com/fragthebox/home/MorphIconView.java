package com.fragthebox.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.view.View;

import androidx.core.graphics.PathParser;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A stroked Lucide icon that morphs into another on a spring, the native take on Morphicons: every outline of
 * both icons is resampled into the same number of points, outlines are paired longest with longest (a spare
 * one shrinks into its own centre or grows out of it), closed outlines are turned to the start point that
 * travels least, and the points glide from one icon to the other.
 */
final class MorphIconView extends View {

    private static final int N = 72;
    private static final float CENTRE_EPS = 0.35f;

    private static final class Contour {
        final float[] pts = new float[N * 2];
        boolean closed;
        float length;
    }

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private List<Contour> from = new ArrayList<>(), to = new ArrayList<>();
    private String[] icon;
    private float progress = 1f;
    private ColorStateList tint = ColorStateList.valueOf(Color.WHITE);
    private final SpringAnimation spring;

    MorphIconView(Context ctx) {
        super(ctx);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(Color.WHITE);
        spring = new SpringAnimation(this, new FloatPropertyCompat<MorphIconView>("morph") {
            @Override
            public float getValue(MorphIconView v) {
                return v.progress;
            }

            @Override
            public void setValue(MorphIconView v, float value) {
                v.progress = value;
                v.invalidate();
            }
        });
        // A little life at the end, as Morphicons has, but no visible wobble.
        spring.setSpring(new SpringForce(1f).setStiffness(320f).setDampingRatio(0.72f));
        spring.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE);
    }

    void setTint(ColorStateList list) {
        tint = list;
        refreshColor();
    }

    /** Shows {@code next}; with {@code animate}, morphs into it from what is on screen now. */
    void setIcon(String[] next, boolean animate) {
        if (next == icon) return;
        List<Contour> target = sample(next);
        if (!animate || icon == null) {
            spring.cancel();
            from = target;
            to = target;
            progress = 1f;
        } else {
            from = current();
            to = target;
            pair(from, to);
            progress = 0f;
            spring.setStartValue(0f);
            spring.animateToFinalPosition(1f);
        }
        icon = next;
        invalidate();
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        refreshColor();
    }

    private void refreshColor() {
        stroke.setColor(tint.getColorForState(getDrawableState(), tint.getDefaultColor()));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float scale = Math.min(getWidth(), getHeight()) / 24f;
        if (scale <= 0) return;
        canvas.save();
        canvas.translate((getWidth() - 24 * scale) / 2f, (getHeight() - 24 * scale) / 2f);
        canvas.scale(scale, scale);
        stroke.setStrokeWidth(2f);
        int count = Math.min(from.size(), to.size());
        for (int c = 0; c < count; c++) {
            Contour a = from.get(c), b = to.get(c);
            path.rewind();
            float minX = 99, maxX = -99, minY = 99, maxY = -99;
            for (int i = 0; i < N; i++) {
                float x = a.pts[2 * i] + (b.pts[2 * i] - a.pts[2 * i]) * progress;
                float y = a.pts[2 * i + 1] + (b.pts[2 * i + 1] - a.pts[2 * i + 1]) * progress;
                if (i == 0) path.moveTo(x, y);
                else path.lineTo(x, y);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
            // An outline shrinking into its centre stops drawing before it becomes a round-capped dot.
            if (maxX - minX < CENTRE_EPS && maxY - minY < CENTRE_EPS && (a.length == 0 || b.length == 0)) continue;
            if (progress < 0.5f ? a.closed : b.closed) path.close();
            canvas.drawPath(path, stroke);
        }
        canvas.restore();
    }

    /** The icon as drawn at this moment, so a new morph starts from wherever the last one had got to. */
    private List<Contour> current() {
        List<Contour> now = new ArrayList<>();
        int count = Math.min(from.size(), to.size());
        for (int c = 0; c < count; c++) {
            Contour a = from.get(c), b = to.get(c), m = new Contour();
            for (int i = 0; i < N * 2; i++) m.pts[i] = a.pts[i] + (b.pts[i] - a.pts[i]) * progress;
            m.closed = progress < 0.5f ? a.closed : b.closed;
            m.length = progress < 0.5f ? a.length : b.length;
            if (m.length > 0) now.add(m);
        }
        return now;
    }

    private static List<Contour> sample(String[] icon) {
        List<Contour> out = new ArrayList<>();
        float[] pos = new float[2];
        for (String d : icon) {
            Path p = PathParser.createPathFromPathData(d);
            PathMeasure pm = new PathMeasure(p, false);
            do {
                float len = pm.getLength();
                if (len <= 0.01f) continue;
                Contour c = new Contour();
                c.closed = pm.isClosed();
                c.length = len;
                for (int i = 0; i < N; i++) {
                    float t = c.closed ? len * i / N : len * i / (N - 1);
                    pm.getPosTan(t, pos, null);
                    c.pts[2 * i] = pos[0];
                    c.pts[2 * i + 1] = pos[1];
                }
                out.add(c);
            } while (pm.nextContour());
        }
        Collections.sort(out, (x, y) -> Float.compare(y.length, x.length));
        return out;
    }

    /** Pads the shorter list with collapsed outlines and turns closed outlines to their best start point. */
    private static void pair(List<Contour> a, List<Contour> b) {
        while (a.size() < b.size()) a.add(collapsed(b.get(a.size())));
        while (b.size() < a.size()) b.add(collapsed(a.get(b.size())));
        for (int c = 0; c < a.size(); c++) {
            Contour x = a.get(c), y = b.get(c);
            if (y.closed && x.length > 0) rotate(y, bestShift(x, y));
        }
    }

    private static Contour collapsed(Contour like) {
        Contour c = new Contour();
        float cx = 0, cy = 0;
        for (int i = 0; i < N; i++) {
            cx += like.pts[2 * i];
            cy += like.pts[2 * i + 1];
        }
        cx /= N;
        cy /= N;
        for (int i = 0; i < N; i++) {
            c.pts[2 * i] = cx;
            c.pts[2 * i + 1] = cy;
        }
        c.closed = like.closed;
        c.length = 0;
        return c;
    }

    private static int bestShift(Contour a, Contour b) {
        int best = 0;
        float bestCost = Float.MAX_VALUE;
        for (int s = 0; s < N; s++) {
            float cost = 0;
            for (int i = 0; i < N; i++) {
                int j = (i + s) % N;
                float dx = a.pts[2 * i] - b.pts[2 * j], dy = a.pts[2 * i + 1] - b.pts[2 * j + 1];
                cost += dx * dx + dy * dy;
            }
            if (cost < bestCost) {
                bestCost = cost;
                best = s;
            }
        }
        return best;
    }

    private static void rotate(Contour c, int shift) {
        if (shift == 0) return;
        float[] copy = c.pts.clone();
        for (int i = 0; i < N; i++) {
            int j = (i + shift) % N;
            c.pts[2 * i] = copy[2 * j];
            c.pts[2 * i + 1] = copy[2 * j + 1];
        }
    }
}
