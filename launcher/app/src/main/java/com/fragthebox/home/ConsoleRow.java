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
        /** OK held on a tile: the tile's menu (Move, Remove from Home). */
        void onMenu(Item item);
        /** Move mode ended with the tiles in a new order. */
        void onReordered(List<Item> order);
    }

    static final class Item {
        String title;
        Drawable art;          // app banner, filling the tile (centre crop), as cleanyourtv draws it
        Drawable icon;         // phone app with no banner: its icon centred over its name
        Bitmap poster;         // continue-watching art, drawn full bleed
        int bg = 0xFF1C1F26;
        int accent = 0xFF5A6B8C;  // the ambient glow's colour while this card is focused
        boolean resume;        // the continue-watching card: percent, "In progress", Resume
        float progress;
        String source;
        String pkg;            // the app behind the tile; null for the Continue watching card
        Runnable open;
    }

    static final float CARD_W = 180f, CARD_H = 101f, GAP = 22f, FOCUS_SCALE = 1.10f, RADIUS = 16f;
    /** A picked-up tile (Move) sits a little larger than a focused one. */
    private static final float MOVE_SCALE = 1.16f;
    private static final float STIFFNESS = 420f, DAMPING = 0.92f;

    private final float density;
    private final List<Item> items = new ArrayList<>();
    private final List<View> cards = new ArrayList<>();
    private int focus;
    private Listener listener;
    private GlassView halo;
    /** How far the row is scrolled: only as much as keeps the focused tile inside the band. */
    private float offset;
    private boolean moving, longPressed;

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
            addView(card, new LayoutParams(px(CARD_W), px(CARD_H), Gravity.CENTER_VERTICAL | Gravity.START));
        }
        // The glass rim and soft glow of the classic launcher; it travels with the focused tile.
        float inset = 6f;
        halo = new GlassView(getContext(), null);
        halo.setStyle(Config.GLASS_HALO);
        halo.setRadius(px(RADIUS) * FOCUS_SCALE);
        LayoutParams hlp = new LayoutParams(px(CARD_W * FOCUS_SCALE + 2 * inset), px(CARD_H * FOCUS_SCALE + 2 * inset),
                Gravity.CENTER_VERTICAL | Gravity.START);
        // Centred on the focused tile, which grows from its own centre-left inside the band.
        hlp.leftMargin = -px(inset);
        addView(halo, hlp);
        int keep = was == null ? 0 : indexOfTitle(was.title);
        focus = keep < 0 ? 0 : Math.min(keep, Math.max(0, items.size() - 1));
        moving = false;
        layoutCards(false);
        if (listener != null && !items.isEmpty()) listener.onFocused(items.get(focus));
    }

    private int indexOfTitle(String title) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).title.equals(title)) return i;
        return -1;
    }

    /** Back to the first card, the Home button's job. */
    void home() {
        if (moving) endMove();
        if (focus == 0) return;
        focus = 0;
        layoutCards(true);
        if (listener != null && !items.isEmpty()) listener.onFocused(items.get(0));
    }

    /** Picks the focused tile up: Left and Right carry it, OK or Back puts it down. */
    void startMove() {
        if (items.isEmpty() || items.get(focus).pkg == null) return;
        moving = true;
        layoutCards(true);
    }

    boolean isMoving() {
        return moving;
    }

    private void endMove() {
        moving = false;
        layoutCards(true);
        if (listener != null) listener.onReordered(new ArrayList<>(items));
    }

    private static boolean isOk(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (items.isEmpty()) return super.onKeyDown(keyCode, event);
        if (moving) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                swap(focus, focus - 1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                swap(focus, focus + 1);
                return true;
            }
            if (isOk(keyCode) || keyCode == KeyEvent.KEYCODE_BACK) {
                endMove();
                return true;
            }
            return true;   // up and down stay in the row while a tile is picked up
        }
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (focus > 0) move(focus - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (focus < items.size() - 1) move(focus + 1);
                return true;
            default:
                if (isOk(keyCode)) {
                    // Opened on release, so holding OK can open the tile's menu instead.
                    if (event.getRepeatCount() == 0) {
                        event.startTracking();
                        longPressed = false;
                    }
                    return true;
                }
                return super.onKeyDown(keyCode, event);
        }
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        if (isOk(keyCode) && !moving && !items.isEmpty()) {
            longPressed = true;
            if (listener != null) listener.onMenu(items.get(focus));
            return true;
        }
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (isOk(keyCode) && !moving) {
            if (!longPressed && event.isTracking() && !event.isCanceled() && listener != null && !items.isEmpty()) {
                listener.onOpen(items.get(focus));
            }
            longPressed = false;
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    /** Carries the picked-up tile one place left or right; the Continue watching card stays first. */
    private void swap(int from, int to) {
        if (to < 0 || to >= items.size() || items.get(to).pkg == null) return;
        java.util.Collections.swap(items, from, to);
        java.util.Collections.swap(cards, from, to);
        focus = to;
        layoutCards(true);
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

    /**
     * Targets for every card. Tiles keep their places and focus moves along them; the row scrolls only as far
     * as it must to keep the focused tile inside the band, so nothing disappears to the left.
     */
    private void layoutCards(boolean animate) {
        float step = px(CARD_W + GAP);
        boolean active = hasFocus() || isFocused();
        float focusScale = !active ? 1f : moving ? MOVE_SCALE : FOCUS_SCALE;
        float grow = px(CARD_W) * (focusScale - 1f);
        float focusLeft = focus * step;
        float focusRight = focusLeft + px(CARD_W) * focusScale;
        float width = getWidth() > 0 ? getWidth() : getResources().getDisplayMetrics().widthPixels;
        offset = Math.max(Math.min(offset, focusLeft), focusRight - width);
        offset = Math.max(0f, offset);
        if (halo != null) {
            spring(halo, DynamicAnimation.ALPHA, active && !moving && !items.isEmpty() ? 1f : 0f, animate);
            spring(halo, DynamicAnimation.TRANSLATION_X, focusLeft - offset, animate);
        }
        for (int j = 0; j < cards.size(); j++) {
            View c = cards.get(j);
            float x = j * step + (j > focus ? grow : 0f) - offset;
            float scale = j == focus ? focusScale : 1f;
            float alpha = 1f;
            c.setPivotX(0f);
            c.setPivotY(px(CARD_H) / 2f);
            spring(c, DynamicAnimation.TRANSLATION_X, x, animate);
            spring(c, DynamicAnimation.SCALE_X, scale, animate);
            spring(c, DynamicAnimation.SCALE_Y, scale, animate);
            spring(c, DynamicAnimation.ALPHA, alpha, animate);
            View details = c.findViewWithTag("details");
            if (details != null) {
                spring(details, DynamicAnimation.ALPHA, j == focus ? 1f : 0f, animate);
            }
            c.setElevation(j == focus ? px(moving ? 18 : 10) : px(2));
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
            art.setScaleType(ImageView.ScaleType.CENTER_CROP);
            art.setImageDrawable(it.art);
            card.addView(art, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else if (it.icon != null) {
            // cleanyourtv's icon tile: a 48 dp icon a little above centre, the app's name along the bottom.
            ImageView ic = new ImageView(ctx);
            ic.setImageDrawable(it.icon);
            LayoutParams ilp = new LayoutParams(px(48), px(48), Gravity.CENTER);
            ilp.bottomMargin = px(14);
            card.addView(ic, ilp);
            TextView name = text(ctx, it.title, 13, Typeface.NORMAL, Color.WHITE);
            name.setGravity(Gravity.CENTER);
            name.setPadding(px(10), 0, px(10), px(8));
            card.addView(name, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
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
