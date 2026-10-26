package com.fragthebox.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import java.util.ArrayList;
import java.util.List;

/**
 * The console row: one line of cards where the focused card always sits first and about a quarter larger
 * (measured from the owner's reference: 159x162 against 128x130 dp, 29 dp apart). Cards are laid out once
 * at their small size and only move by translation and scale, so a step left or right never relayouts;
 * every move runs on a spring with no overshoot.
 */
final class ConsoleRow extends FrameLayout {

    interface Listener {
        void onOpen(Item item);
        void onFocused(Item item);
    }

    static final class Item {
        String title;
        Drawable art;          // app banner, drawn fitted on bg
        Bitmap poster;         // continue-watching art, drawn full bleed
        int bg = 0xFF1C1F26;
        boolean resume;        // the continue-watching card: percent, "In progress", Resume
        float progress;
        String source;
        Runnable open;
    }

    static final float CARD_W = 128f, CARD_H = 130f, GAP = 29f, FOCUS_SCALE = 1.245f, RADIUS = 17f;
    private static final float STIFFNESS = 420f, DAMPING = 0.92f;

    private final float density;
    private final List<Item> items = new ArrayList<>();
    private final List<View> cards = new ArrayList<>();
    private int focus;
    private Listener listener;

    ConsoleRow(Context ctx) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        setFocusable(true);
        setClipChildren(false);
        setClipToPadding(false);
    }

    void setListener(Listener l) {
        listener = l;
    }

    Item focusedItem() {
        return focus < items.size() ? items.get(focus) : null;
    }

    void setItems(List<Item> list) {
        Item was = focusedItem();
        removeAllViews();
        cards.clear();
        items.clear();
        items.addAll(list);
        for (Item it : items) {
            View card = buildCard(it);
            cards.add(card);
            addView(card, new LayoutParams(px(CARD_W), px(CARD_H), Gravity.BOTTOM | Gravity.START));
        }
        int keep = was == null ? 0 : indexOfTitle(was.title);
        focus = keep < 0 ? 0 : Math.min(keep, Math.max(0, items.size() - 1));
        layoutCards(false);
    }

    private int indexOfTitle(String title) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).title.equals(title)) return i;
        return -1;
    }

    /** Back to the first card, the Home button's job. */
    void home() {
        if (focus == 0) return;
        focus = 0;
        layoutCards(true);
        if (listener != null && !items.isEmpty()) listener.onFocused(items.get(0));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (items.isEmpty()) return super.onKeyDown(keyCode, event);
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (focus > 0) move(focus - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (focus < items.size() - 1) move(focus + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (listener != null) listener.onOpen(items.get(focus));
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    private void move(int to) {
        focus = to;
        layoutCards(true);
        if (listener != null) listener.onFocused(items.get(focus));
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, android.graphics.Rect previous) {
        super.onFocusChanged(gained, direction, previous);
        layoutCards(true);
    }

    /** Targets for every card: the focused one at the row's start, grown from its bottom-left corner. */
    private void layoutCards(boolean animate) {
        float step = px(CARD_W + GAP);
        float grow = px(CARD_W) * (FOCUS_SCALE - 1f);
        boolean active = hasFocus() || isFocused();
        for (int j = 0; j < cards.size(); j++) {
            View c = cards.get(j);
            float x = (j - focus) * step + (j > focus ? grow : 0f);
            float scale = j == focus ? (active ? FOCUS_SCALE : 1.12f) : 1f;
            float alpha = j < focus ? 0f : 1f;
            c.setPivotX(0f);
            c.setPivotY(px(CARD_H));
            spring(c, DynamicAnimation.TRANSLATION_X, x, animate);
            spring(c, DynamicAnimation.SCALE_X, scale, animate);
            spring(c, DynamicAnimation.SCALE_Y, scale, animate);
            spring(c, DynamicAnimation.ALPHA, alpha, animate);
            View details = c.findViewWithTag("details");
            if (details != null) {
                spring(details, DynamicAnimation.ALPHA, j == focus ? 1f : 0f, animate);
            }
            c.setElevation(j == focus ? px(10) : px(2));
        }
    }

    private void spring(View v, DynamicAnimation.ViewProperty prop, float target, boolean animate) {
        int key = prop == DynamicAnimation.TRANSLATION_X ? R.id.spring_tx
                : prop == DynamicAnimation.SCALE_X ? R.id.spring_sx
                : prop == DynamicAnimation.SCALE_Y ? R.id.spring_sy : R.id.spring_alpha;
        SpringAnimation a = (SpringAnimation) v.getTag(key);
        if (!animate) {
            if (a != null) a.cancel();
            prop.setValue(v, target);
            return;
        }
        if (a == null) {
            a = new SpringAnimation(v, prop);
            a.setSpring(new SpringForce().setStiffness(STIFFNESS).setDampingRatio(DAMPING));
            if (prop != DynamicAnimation.TRANSLATION_X) a.setMinimumVisibleChange(DynamicAnimation.MIN_VISIBLE_CHANGE_SCALE);
            v.setTag(key, a);
        }
        a.animateToFinalPosition(target);
    }

    private View buildCard(Item it) {
        Context ctx = getContext();
        FrameLayout card = new FrameLayout(ctx);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(it.bg);
        bg.setCornerRadius(px(RADIUS));
        card.setBackground(bg);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), px(RADIUS));
            }
        });
        card.setClipToOutline(true);
        // A hairline rim keeps light art (Netflix, YouTube) from bleeding into a bright wallpaper.
        GradientDrawable rim = new GradientDrawable();
        rim.setCornerRadius(px(RADIUS));
        rim.setStroke(Math.max(1, px(0.7f)), 0x24FFFFFF);
        card.setForeground(rim);

        if (it.poster != null) {
            ImageView art = new ImageView(ctx);
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageBitmap(it.poster);
            card.addView(art, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (it.art != null) {
            ImageView art = new ImageView(ctx);
            art.setScaleType(ImageView.ScaleType.FIT_CENTER);
            art.setImageDrawable(it.art);
            card.addView(art, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(CARD_W * 9f / 16f), Gravity.CENTER));
        } else {
            TextView name = text(ctx, it.title, 15, Typeface.BOLD, Color.WHITE);
            name.setGravity(Gravity.CENTER);
            card.addView(name, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        if (it.resume) {
            // Bottom shade and the reference's text block: 45%, an outlined "In progress" tag, ▶ Resume.
            View shade = new View(ctx);
            shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    new int[]{0x000B1220, 0xCC0B1220, 0xF00B1220}));
            card.addView(shade, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(CARD_H * 0.62f), Gravity.BOTTOM));

            LinearLayout details = new LinearLayout(ctx);
            details.setTag("details");
            details.setOrientation(LinearLayout.VERTICAL);
            details.setPadding(px(9), 0, px(9), px(8));
            details.addView(text(ctx, Math.round(it.progress * 100) + "%", 16, Typeface.BOLD, Color.WHITE));
            TextView tag = text(ctx, ctx.getString(R.string.console_in_progress), 7.5f, Typeface.NORMAL, Color.WHITE);
            GradientDrawable outline = new GradientDrawable();
            outline.setStroke(Math.max(1, px(0.8f)), 0xE6FFFFFF);
            outline.setCornerRadius(px(2.2f));
            tag.setBackground(outline);
            tag.setPadding(px(6), px(1), px(6), px(1.5f));
            LinearLayout.LayoutParams tagLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            tagLp.topMargin = px(2);
            details.addView(tag, tagLp);
            TextView resume = text(ctx, "▶  " + ctx.getString(R.string.console_resume), 8f, Typeface.NORMAL, 0xE6FFFFFF);
            LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rLp.topMargin = px(7);
            details.addView(resume, rLp);
            card.addView(details, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        }
        return card;
    }

    private TextView text(Context ctx, String s, float sp, int style, int color) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setTypeface(Typeface.create(ctx.getResources().getFont(R.font.outfit_medium), style));
        t.setSingleLine(true);
        return t;
    }

    private int px(float dp) {
        return Math.round(dp * density);
    }
}
