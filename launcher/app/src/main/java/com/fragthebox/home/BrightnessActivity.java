package com.fragthebox.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The brightness panel from the top bar's sun button: Auto, five levels, and the TV's own Picture settings for the
 * real backlight. A small glass panel at the top right over Home; Back closes it.
 */
public class BrightnessActivity extends Activity {

    private static final int[] LEVELS = {100, 80, 60, 40, 20};
    private float density;
    private LinearLayout list;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setOnClickListener(v -> finish());
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setBackgroundResource(R.drawable.glass_panel);
        list.setPadding(dp(6), dp(10), dp(6), dp(6));
        list.setElevation(dp(12));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(250), ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        lp.topMargin = dp(86);
        lp.rightMargin = dp(48);
        root.addView(list, lp);
        setContentView(root);
        build();
    }

    private void build() {
        list.removeAllViews();
        TextView head = new TextView(this);
        head.setText(R.string.brightness);
        head.setAllCaps(true);
        head.setLetterSpacing(0.2f);
        head.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        head.setTextColor(0xB3FFFFFF);
        head.setPadding(dp(12), 0, 0, dp(6));
        list.addView(head);
        int level = Brightness.level(this);
        View first = row(getString(R.string.brightness_auto), level == Brightness.AUTO, () -> choose(Brightness.AUTO));
        for (int l : LEVELS) {
            row(l + " %", level == l, () -> choose(l));
        }
        row(getString(R.string.brightness_tv_picture), false, this::openTvPicture);
        View current = list.findViewWithTag("current");
        (current != null ? current : first).requestFocus();
    }

    private View row(String label, boolean on, Runnable click) {
        TextView t = new TextView(this);
        t.setText(on ? label + "   ✓" : label);
        t.setTextColor(getColorStateList(R.drawable.pill_text));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(getResources().getFont(R.font.outfit_regular));
        t.setBackgroundResource(R.drawable.input_row_bg);
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        t.setFocusable(true);
        if (on) t.setTag("current");
        t.setOnClickListener(v -> click.run());
        list.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    private void choose(int level) {
        Brightness.set(this, level);
        if (!Settings.canDrawOverlays(this) && !Brightness.idle(this)) {
            // The dim layer needs "display over other apps"; the installer grants it, this asks where it didn't.
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName())));
            } catch (ActivityNotFoundException ignored) {
            }
        }
        BrightnessService.update(this);
        build();
    }

    /** The TV's own picture menu, where the panel's backlight lives (TCL's panel, else Android's display settings). */
    private void openTvPicture() {
        Intent tcl = new Intent().setComponent(new ComponentName("com.tcl.settings", "com.tcl.settings.MainActivity"));
        for (Intent i : new Intent[]{tcl, new Intent(Settings.ACTION_DISPLAY_SETTINGS), new Intent(Settings.ACTION_SETTINGS)}) {
            try {
                startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                finish();
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
