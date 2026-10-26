package com.fragthebox.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;

import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The console home: a full-screen wallpaper from the owner's GitHub folder, the clock at the top right, one
 * row of cards (Continue watching first, then the TV apps) and a bar of seven icons. Measured from the owner's
 * reference at 960x540 dp. Inputs, All apps and Power open as a dark sheet that rises from the bottom while
 * the row slides up and fades, the transition of the owner's motion reference.
 */
public class ConsoleActivity extends Activity {

    private static final long ROTATE_CHECK_MS = 15 * 60 * 1000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private float density;
    private ImageView wallBack, wallFront;
    private ConsoleRow row;
    private LinearLayout bar;
    private GlassPanel barGlass;
    private GlassPanel band;
    /** The band: 12 dp around the focused tile (101 dp at 1.10x), 88 dp above the screen's bottom. */
    private static final float BAND_PAD = 12f, BAND_H = 111f + 2 * 12f, BAND_BOTTOM = 88f;
    private View homeCell;
    private GradientDrawable glow, bloom, shade;
    private View bloomView;
    private Ambience ambience = new Ambience();
    private int glowColor = 0xFF5A6B8C;
    private android.animation.ValueAnimator glowAnim;
    private TextView title;
    private FrameLayout sheet;
    private MorphIconView wifiIcon, btIcon;
    private ConnectivityManager.NetworkCallback netCallback;
    private GitWallpapers wallpapers;
    private String liveTvPkg = "";
    private boolean sheetOpen;
    private View lastBarFocus;

    private final Runnable rotate = new Runnable() {
        @Override
        public void run() {
            wallpapers.load(ConsoleActivity.this::showWallpaper);
            refreshMood();
            main.postDelayed(this, ROTATE_CHECK_MS);
        }
    };

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshRow();
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        density = getResources().getDisplayMetrics().density;
        setContentView(buildLayout());

        DisplayMetrics dm = getResources().getDisplayMetrics();
        long hours = Math.max(1, getResources().getInteger(R.integer.wallpaper_rotate_hours));
        wallpapers = new GitWallpapers(this, getString(R.string.wallpaper_repo), getString(R.string.wallpaper_path),
                hours * 60 * 60 * 1000L, dm.widthPixels, dm.heightPixels);
        android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        Bitmap builtIn = android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.console_default_wall, o);
        wallBack.setImageBitmap(builtIn);
        applyAmbience(builtIn, false);
        breathe();

        liveTvPkg = findLiveTvPackage();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        registerReceiver(bluetooth, new IntentFilter(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED));
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null) {
            netCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(android.net.Network n) {
                    main.post(ConsoleActivity.this::refreshNetwork);
                }

                @Override
                public void onLost(android.net.Network n) {
                    main.postDelayed(ConsoleActivity.this::refreshNetwork, 300);
                }
            };
            cm.registerDefaultNetworkCallback(netCallback);
        }
        refreshRow();
        row.requestFocus();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        unregisterReceiver(bluetooth);
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null && netCallback != null) cm.unregisterNetworkCallback(netCallback);
        super.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        main.removeCallbacks(rotate);
        main.post(rotate);
        refreshRow();
        refreshNetwork();
    }

    @Override
    protected void onPause() {
        main.removeCallbacks(rotate);
        super.onPause();
    }

    /** Home pressed while Home is showing: close any sheet and go back to the first card. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (sheetOpen) closeSheet();
        row.home();
        row.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (sheetOpen) {
            closeSheet();
        } else if (!row.hasFocus()) {
            row.requestFocus();
        } else {
            row.home();
        }
    }

    // ---- layout -----------------------------------------------------------------------------------

    private View buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setClipChildren(false);

        wallBack = new ImageView(this);
        wallBack.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wallBack, match());
        wallFront = new ImageView(this);
        wallFront.setScaleType(ImageView.ScaleType.CENTER_CROP);
        wallFront.setAlpha(0f);
        root.addView(wallFront, match());

        // The wallpaper's own light, bloomed softly where it comes from, so the picture feels lit, not dimmed.
        bloom = new GradientDrawable();
        bloom.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        bloom.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.55f);
        bloomView = new View(this);
        bloomView.setBackground(bloom);
        root.addView(bloomView, match());

        // A faint glow in the focused app's colour, mixed into the picture's light rather than painted over it.
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.3f, 0.7f);
        glow.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.5f);
        setGlow(glowColor);
        View glowView = new View(this);
        glowView.setBackground(glow);
        root.addView(glowView, match());

        // Shade only under the row and text, in the picture's own darkest tone, never a black band.
        shade = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0, 0, 0});
        View shadeView = new View(this);
        shadeView.setBackground(shade);
        root.addView(shadeView, match());

        // The clock large and thin at the top left, AM/PM small beside it.
        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.HORIZONTAL);
        clockBox.setGravity(Gravity.BOTTOM);
        TextClock time = clockText("h:mm", 50, R.font.outfit_extralight);
        TextClock ampm = clockText("a", 13, R.font.outfit_medium);
        ampm.setAllCaps(true);
        ampm.setPadding(dp(6), 0, 0, dp(9));
        clockBox.addView(time);
        clockBox.addView(ampm);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        clp.topMargin = dp(26);
        clp.leftMargin = dp(60);
        root.addView(clockBox, clp);

        row = new ConsoleRow(this);
        row.setId(View.generateViewId());
        row.setListener(new ConsoleRow.Listener() {
            @Override
            public void onOpen(ConsoleRow.Item item) {
                if (item.open != null) item.open.run();
            }

            @Override
            public void onFocused(ConsoleRow.Item item) {
                showFocused(item);
            }

            @Override
            public void onMenu(ConsoleRow.Item item) {
                if (item.pkg != null) openTileMenu(item);
            }

            @Override
            public void onReordered(List<ConsoleRow.Item> order) {
                List<String> pkgs = new ArrayList<>();
                for (ConsoleRow.Item it : order) if (it.pkg != null) pkgs.add(it.pkg);
                // Apps on Home that aren't installed right now keep their place at the end.
                for (String pkg : homeApps()) if (!pkgs.contains(pkg)) pkgs.add(pkg);
                saveHomeApps(pkgs);
            }
        });

        // One line of serif text above the row that changes with the time of day.
        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 27);
        title.setTypeface(getResources().getFont(R.font.newsreader_serif));
        title.setSingleLine(true);
        title.setShadowLayer(dp(14), 0, dp(1), 0x59000000);
        title.setText(MoodLine.now());
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        tlp.leftMargin = dp(72);
        tlp.bottomMargin = dp(BAND_BOTTOM + BAND_H + 14);
        root.addView(title, tlp);

        // The matte band: the row sits inside it and slides under its rounded ends.
        band = new GlassPanel(this, ConsoleRow.RADIUS + BAND_PAD, GlassPanel.MATTE);
        band.setClipToOutline(true);
        band.setPadding(dp(BAND_PAD), dp(BAND_PAD), dp(BAND_PAD), dp(BAND_PAD));
        band.addView(row, match());
        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(BAND_H), Gravity.BOTTOM);
        rlp.bottomMargin = dp(BAND_BOTTOM);
        rlp.leftMargin = dp(60);
        rlp.rightMargin = dp(60);
        root.addView(band, rlp);

        // Round glass buttons at the top right: Wi-Fi and Bluetooth show their state and morph when it changes.
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        wifiIcon = new MorphIconView(this);
        topButton(top, wifiIcon, LucideIcons.WIFI, R.string.console_network,
                v -> start(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        btIcon = new MorphIconView(this);
        topButton(top, btIcon, bluetoothOn() ? LucideIcons.BLUETOOTH : LucideIcons.BLUETOOTH_OFF,
                R.string.console_bluetooth, v -> start(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)));
        topButton(top, new MorphIconView(this), LucideIcons.SETTINGS, R.string.console_settings,
                v -> openSettings());
        FrameLayout.LayoutParams toplp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        toplp.topMargin = dp(38);
        toplp.rightMargin = dp(60);
        root.addView(top, toplp);

        // The bar: a frosted glass pill, as wide as the reference's and a little taller so the icons breathe.
        bar = buildBar();
        barGlass = new GlassPanel(this, -1, GlassPanel.PILL);
        // One long pill glides between the icons; out of the bar it rests, dimmed, on Home like a selected tab.
        FrameLayout barBox = new FrameLayout(this);
        SlidingPill barPill = new SlidingPill(this, 6, -1);
        barBox.addView(barPill, match());
        barBox.addView(bar, match());
        barPill.follow(bar);
        barPill.restOn(homeCell);
        barGlass.addView(barBox, match());
        barGlass.setPadding(dp(8), 0, dp(8), 0);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(470), dp(46), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        blp.bottomMargin = dp(22);
        root.addView(barGlass, blp);

        // Sheets float as a glass panel with margins on every side, not a slab glued to the screen's edge.
        sheet = new GlassPanel(this, 20, GlassPanel.PANEL);
        sheet.setVisibility(View.GONE);
        // Sized to what it holds: a short list is a small panel, the app grid is as wide as its tiles.
        sheet.setPadding(dp(8), dp(14), dp(8), dp(8));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        slp.bottomMargin = dp(28);
        root.addView(sheet, slp);
        return root;
    }

    private LinearLayout buildBar() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        addBarIcon(b, LucideIcons.TV, R.string.console_inputs, v -> openInputs());
        Intent music = musicApp();
        if (music != null) addBarIcon(b, LucideIcons.HEADPHONES, R.string.console_music, v -> start(music));
        Intent notifications = notificationsPanel();
        if (notifications != null) {
            addBarIcon(b, LucideIcons.BELL, R.string.console_notifications, v -> start(notifications));
        }
        View home = homeCell = addBarIcon(b, LucideIcons.HOUSE, R.string.console_home, v -> {
            row.home();
            row.requestFocus();
        });
        addBarIcon(b, LucideIcons.LAYOUT_GRID, R.string.console_apps, v -> openApps());
        addBarIcon(b, LucideIcons.POWER, R.string.console_power, v -> openPower());
        row.setNextFocusDownId(home.getId());
        return b;
    }

    private View addBarIcon(LinearLayout b, String[] icon, int label, View.OnClickListener click) {
        FrameLayout cell = new FrameLayout(this);
        cell.setId(View.generateViewId());
        cell.setFocusable(true);
        cell.setContentDescription(getString(label));
        cell.setOnClickListener(click);
        cell.setNextFocusUpId(row.getId());
        cell.setOnFocusChangeListener((v, has) -> {
            if (has) lastBarFocus = v;
        });

        MorphIconView iv = new MorphIconView(this);
        iv.setIcon(icon, false);
        iv.setDuplicateParentStateEnabled(true);
        iv.setTint(onPill());
        cell.addView(iv, new FrameLayout.LayoutParams(dp(19), dp(19), Gravity.CENTER));
        b.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return cell;
    }

    /** A round glass button for the top right: pill_bg's glass at rest, the white pill while focused. */
    private View topButton(LinearLayout parent, MorphIconView icon, String[] shape, int label, View.OnClickListener click) {
        FrameLayout b = new FrameLayout(this);
        b.setId(View.generateViewId());
        b.setFocusable(true);
        b.setContentDescription(getString(label));
        b.setOnClickListener(click);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, circle(0xF5FFFFFF));
        GradientDrawable rest = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0x2EFFFFFF, 0x14FFFFFF});
        rest.setShape(GradientDrawable.OVAL);
        rest.setStroke(dp(1), 0x38FFFFFF);
        bg.addState(new int[]{}, rest);
        b.setBackground(bg);
        icon.setIcon(shape, false);
        icon.setDuplicateParentStateEnabled(true);
        icon.setTint(onPill());
        b.addView(icon, new FrameLayout.LayoutParams(dp(17), dp(17), Gravity.CENTER));
        b.setNextFocusDownId(row.getId());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(38), dp(38));
        if (parent.getChildCount() > 0) lp.leftMargin = dp(10);
        parent.addView(b, lp);
        if (parent.getChildCount() == 1) row.setNextFocusUpId(b.getId());
        return b;
    }

    private TextClock clockText(String format, float sp, int font) {
        TextClock t = new TextClock(this);
        t.setFormat12Hour(format);
        t.setFormat24Hour(format);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(getResources().getFont(font));
        t.setIncludeFontPadding(false);
        t.setShadowLayer(dp(10), 0, dp(1), 0x66000000);   // readable over a bright wallpaper
        return t;
    }

    private boolean bluetoothOn() {
        try {
            android.bluetooth.BluetoothAdapter a = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            return a != null && a.isEnabled();
        } catch (SecurityException e) {
            return true;
        }
    }

    private final BroadcastReceiver bluetooth = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            btIcon.setIcon(bluetoothOn() ? LucideIcons.BLUETOOTH : LucideIcons.BLUETOOTH_OFF, true);
        }
    };

    private GradientDrawable circle(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        return g;
    }

    // ---- focus: glow and title -----------------------------------------------------------------------

    private void showFocused(ConsoleRow.Item item) {
        if (glowAnim != null) glowAnim.cancel();
        glowAnim = android.animation.ValueAnimator.ofObject(new android.animation.ArgbEvaluator(), glowColor, item.accent);
        glowAnim.setDuration(450);
        glowAnim.setInterpolator(new DecelerateInterpolator());
        glowAnim.addUpdateListener(a -> setGlow((int) a.getAnimatedValue()));
        glowAnim.start();

    }

    /** The serif line, changed with a soft fade when the time of day moves on. */
    private void refreshMood() {
        String line = MoodLine.now();
        if (line.contentEquals(title.getText())) return;
        title.animate().cancel();
        title.animate().alpha(0f).setDuration(220).withEndAction(() -> {
            title.setText(line);
            title.animate().alpha(1f).setDuration(420).setInterpolator(new DecelerateInterpolator()).start();
        }).start();
    }

    private void setGlow(int color) {
        glowColor = color;
        int c = Ambience.mix(color, ambience.lightColor, 0.45f);
        glow.setColors(new int[]{Ambience.withAlpha(c, 0x47), Ambience.withAlpha(c, 0x14), 0x00000000});
    }

    /** Re-lights the screen from a new wallpaper: bloom, shade, glow and glass all take its colours. */
    private void applyAmbience(Bitmap wall, boolean animate) {
        ambience = Ambience.of(wall);
        bloom.setGradientCenter(ambience.lightX, ambience.lightY);
        // Brighter pictures get a gentler bloom: the light is already there.
        int a = Math.round(0x59 - 0x30 * ambience.brightness);
        bloom.setColors(new int[]{Ambience.withAlpha(ambience.lightColor, a),
                Ambience.withAlpha(ambience.lightColor, a / 4), 0x00000000});
        int floor = ambience.floorColor;
        // Bright daylight pictures need more ground under the row and text than night ones.
        int depth = Math.round(0x70 + 0x58 * ambience.brightness);
        shade.setColors(new int[]{0x00000000, 0x00000000, Ambience.withAlpha(floor, depth / 2), Ambience.withAlpha(floor, depth)});
        int glassTint = Ambience.darken(ambience.moodColor, 0.45f * ambience.brightness);
        band.setTint(glassTint);
        barGlass.setTint(glassTint);
        title.setShadowLayer(dp(14 + 8 * ambience.brightness), 0, dp(1), Ambience.withAlpha(0xFF000000,
                Math.round(0x59 + 0x60 * ambience.brightness)));
        setGlow(glowColor);
        if (animate) {
            bloomView.setAlpha(0f);
            bloomView.animate().alpha(1f).setDuration(900).start();
        }
    }

    /** The picture drifts and its light breathes, very slowly: a living backdrop that never draws the eye. */
    private void breathe() {
        for (View v : new View[]{wallBack, wallFront}) {
            android.animation.ObjectAnimator sx = android.animation.ObjectAnimator.ofFloat(v, View.SCALE_X, 1f, 1.05f);
            android.animation.ObjectAnimator sy = android.animation.ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f, 1.05f);
            for (android.animation.ObjectAnimator o : new android.animation.ObjectAnimator[]{sx, sy}) {
                o.setDuration(45000);
                o.setRepeatMode(android.animation.ValueAnimator.REVERSE);
                o.setRepeatCount(android.animation.ValueAnimator.INFINITE);
                o.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
                o.start();
            }
        }
        android.animation.ObjectAnimator light = android.animation.ObjectAnimator.ofFloat(bloomView, View.ALPHA, 1f, 0.7f);
        light.setDuration(9000);
        light.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        light.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        light.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        light.start();
    }

    // ---- wallpaper ---------------------------------------------------------------------------------

    private void showWallpaper(Bitmap b) {
        applyAmbience(b, true);
        wallFront.setImageBitmap(b);
        wallFront.animate().cancel();
        wallFront.setAlpha(0f);
        wallFront.animate().alpha(1f).setDuration(600).setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    wallBack.setImageBitmap(b);
                    wallFront.setAlpha(0f);
                    wallFront.setImageDrawable(null);
                }).start();
    }

    // ---- the row ------------------------------------------------------------------------------------

    private static final String PREFS = "console_home";

    /** The apps on Home, in order: the owner's saved list, or config.xml home_apps until one is saved. */
    private List<String> homeApps() {
        String saved = getSharedPreferences(PREFS, MODE_PRIVATE).getString("home_apps", null);
        if (saved == null) return new ArrayList<>(Arrays.asList(getResources().getStringArray(R.array.home_apps)));
        List<String> out = new ArrayList<>();
        for (String pkg : saved.split(",")) if (!pkg.isEmpty()) out.add(pkg);
        return out;
    }

    private void saveHomeApps(List<String> pkgs) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString("home_apps", android.text.TextUtils.join(",", pkgs)).apply();
    }

    private void setOnHome(String pkg, boolean on) {
        List<String> pkgs = homeApps();
        pkgs.remove(pkg);
        if (on) pkgs.add(pkg);
        saveHomeApps(pkgs);
        refreshRow();
    }

    /** Hold OK on a tile: Move (Left and Right carry it, OK puts it down), Remove from Home, App info. */
    private void openTileMenu(ConsoleRow.Item item) {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        TextView head = sheetTitle(R.string.console_apps);
        head.setText(item.title);
        list.addView(head);
        list.addView(sheetItem(getString(R.string.console_move), v -> {
            closeSheet();
            row.requestFocus();
            row.post(row::startMove);
        }));
        list.addView(sheetItem(getString(R.string.console_remove_from_home), v -> {
            closeSheet();
            row.requestFocus();
            setOnHome(item.pkg, false);
        }));
        list.addView(sheetItem(getString(R.string.console_app_info), v -> start(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + item.pkg)))));
        openSheet(list, -1);
    }

    private void refreshRow() {
        new Thread(() -> {
            ContinueWatching.Entry cont = ContinueWatching.latest(this);
            List<AppInfo> apps = loadApps();
            main.post(() -> {
                List<ConsoleRow.Item> items = new ArrayList<>();
                if (cont != null) {
                    ConsoleRow.Item it = new ConsoleRow.Item();
                    it.title = cont.title;
                    it.poster = cont.poster;
                    it.resume = true;
                    it.progress = cont.progress;
                    it.bg = 0xFF121722;
                    it.open = () -> start(cont.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    items.add(it);
                }
                // Only the apps on Home, in the owner's order; everything else is in All apps.
                java.util.Map<String, AppInfo> byPkg = new java.util.HashMap<>();
                for (AppInfo a : apps) byPkg.put(a.pkg, a);
                List<AppInfo> onHome = new ArrayList<>();
                for (String pkg : homeApps()) if (byPkg.containsKey(pkg)) onHome.add(byPkg.get(pkg));
                for (AppInfo a : onHome) {
                    ConsoleRow.Item it = new ConsoleRow.Item();
                    it.title = a.label;
                    it.pkg = a.pkg;
                    if (a.tvBanner) it.art = a.banner;
                    else it.icon = a.banner;
                    it.bg = a.tvBanner ? a.bg : 0xFF1C1C1E;
                    it.accent = a.accent;
                    it.open = () -> launch(a);
                    items.add(it);
                }
                row.setItems(items);
            });
        }, "console-row").start();
    }

    private static final class AppInfo {
        String pkg, label;
        ComponentName component;
        Drawable banner;
        boolean tvBanner;
        int bg, accent;
    }

    /** Glow colour by package and install time, so a return to Home doesn't scan every banner again. */
    private static final java.util.Map<String, Integer> ACCENT_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static long updatedAt(PackageManager pm, String pkg) {
        try {
            return pm.getPackageInfo(pkg, 0).lastUpdateTime;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    private List<AppInfo> loadApps() {
        PackageManager pm = getPackageManager();
        Set<String> hidden = new HashSet<>(Arrays.asList(getResources().getStringArray(R.array.hidden_apps)));
        hidden.add(getPackageName());
        if (!liveTvPkg.isEmpty()) hidden.add(liveTvPkg);
        List<String> dock = Arrays.asList(getResources().getStringArray(R.array.dock_apps));
        List<AppInfo> apps = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0)) {
            ActivityInfo ai = ri.activityInfo;
            if (hidden.contains(ai.packageName) || !seen.add(ai.packageName)) continue;
            AppInfo a = new AppInfo();
            a.pkg = ai.packageName;
            a.label = String.valueOf(ri.loadLabel(pm));
            a.component = new ComponentName(ai.packageName, ai.name);
            Drawable banner = ai.loadBanner(pm);
            if (banner == null) banner = ai.applicationInfo.loadBanner(pm);
            a.tvBanner = banner != null;
            if (banner == null) banner = ri.loadIcon(pm);
            a.banner = banner;
            String key = ai.packageName + "@" + updatedAt(pm, ai.packageName);
            Integer accent = ACCENT_CACHE.get(key);
            if (accent == null) {
                accent = CardArt.accent(banner);
                ACCENT_CACHE.put(key, accent);
            }
            a.accent = accent;
            a.bg = edgeColor(banner);
            apps.add(a);
        }
        Collator collator = Collator.getInstance();
        Collections.sort(apps, (x, y) -> {
            int dx = dock.indexOf(x.pkg), dy = dock.indexOf(y.pkg);
            if (dx >= 0 || dy >= 0) {
                if (dx < 0) return 1;
                if (dy < 0) return -1;
                return Integer.compare(dx, dy);
            }
            return collator.compare(x.label, y.label);
        });
        return apps;
    }

    /** The banner's corner colour, so the card around a 16:9 banner reads as one piece of art. */
    private static int edgeColor(Drawable d) {
        try {
            Bitmap b = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(b);
            d.setBounds(0, 0, 32, 18);
            d.draw(c);
            int[] pts = {b.getPixel(1, 1), b.getPixel(30, 1), b.getPixel(1, 16), b.getPixel(30, 16)};
            int r = 0, g = 0, bl = 0, n = 0;
            for (int p : pts) {
                if (Color.alpha(p) < 128) continue;
                r += Color.red(p);
                g += Color.green(p);
                bl += Color.blue(p);
                n++;
            }
            b.recycle();
            return n == 0 ? 0xFF1C1F26 : Color.rgb(r / n, g / n, bl / n);
        } catch (RuntimeException e) {
            return 0xFF1C1F26;
        }
    }

    private void launch(AppInfo a) {
        Intent i = new Intent(Intent.ACTION_MAIN).setComponent(a.component)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        start(i);
    }

    private void start(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException | SecurityException ignored) {
        }
    }

    // ---- bar actions ------------------------------------------------------------------------------

    private Intent musicApp() {
        PackageManager pm = getPackageManager();
        for (String pkg : getResources().getStringArray(R.array.music_apps)) {
            Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (i == null) i = pm.getLaunchIntentForPackage(pkg);
            if (i != null) return i;
        }
        return null;
    }

    private Intent notificationsPanel() {
        for (String action : new String[]{"com.android.tv.action.OPEN_NOTIFICATIONS_PANEL",
                "android.app.action.TOGGLE_NOTIFICATION_HANDLER_PANEL"}) {
            Intent i = new Intent(action);
            if (getPackageManager().resolveActivity(i, 0) != null) return i;
        }
        return null;
    }

    private void refreshNetwork() {
        boolean online = false;
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm != null && cm.getActiveNetwork() != null) {
            NetworkCapabilities caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
            online = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        wifiIcon.setIcon(online ? LucideIcons.WIFI : LucideIcons.WIFI_OFF, true);
    }

    private String findLiveTvPackage() {
        String configured = getString(R.string.live_tv_package);
        PackageManager pm = getPackageManager();
        if (!configured.isEmpty() && pm.getLaunchIntentForPackage(configured) != null) return configured;
        if (!configured.isEmpty() && pm.getLeanbackLaunchIntentForPackage(configured) != null) return configured;
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(
                TvContract.buildChannelUriForPassthroughInput("probe"), "vnd.android.cursor.item/channel");
        ResolveInfo ri = pm.resolveActivity(view, 0);
        return ri == null || ri.activityInfo == null || "android".equals(ri.activityInfo.packageName)
                ? "" : ri.activityInfo.packageName;
    }

    // ---- sheets -----------------------------------------------------------------------------------

    private void openSettings() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.addView(sheetTitle(R.string.console_settings));
        list.addView(sheetItem(getString(R.string.console_wallpaper), v -> openWallpapers()));
        list.addView(sheetItem(getString(R.string.console_tv_settings), v -> start(new Intent(Settings.ACTION_SETTINGS))));
        openSheet(list, -1);
    }

    /** Pick one picture to keep, or let them rotate; the pictures are this repo's wallpapers folder. */
    private void openWallpapers() {
        wallpapers.list((urls, thumbs, pinned) -> {
            LinearLayout box = new LinearLayout(this);
            box.setOrientation(LinearLayout.VERTICAL);
            box.addView(sheetTitle(R.string.console_wallpaper));
            View rotateItem = sheetItem(getString(pinned.isEmpty() ? R.string.console_wall_rotate_on
                    : R.string.console_wall_rotate), v -> {
                closeSheet();
                wallpapers.pin(null, this::showWallpaper);
            });
            box.addView(rotateItem);
            if (urls.isEmpty()) {
                TextView none = sheetTitle(R.string.console_wall_offline);
                none.setAllCaps(false);
                box.addView(none);
                openSheet(box, -1);
                return;
            }
            LinearLayout strip = new LinearLayout(this);
            strip.setOrientation(LinearLayout.HORIZONTAL);
            strip.setPadding(0, dp(6), 0, 0);
            for (int i = 0; i < urls.size(); i++) {
                String url = urls.get(i);
                FrameLayout tile = focusable(new FrameLayout(this));
                tile.setPadding(dp(4), dp(4), dp(4), dp(4));
                ImageView iv = new ImageView(this);
                iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                iv.setBackgroundColor(0xFF1C1C1E);
                iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, android.graphics.Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
                    }
                });
                iv.setClipToOutline(true);
                tile.addView(iv, new FrameLayout.LayoutParams(dp(128), dp(72)));
                if (url.equals(pinned)) {
                    FrameLayout badge = new FrameLayout(this);
                    badge.setBackground(circle(0xF5FFFFFF));
                    MorphIconView check = new MorphIconView(this);
                    check.setTint(android.content.res.ColorStateList.valueOf(0xFF141416));
                    check.setIcon(LucideIcons.CHECK, false);
                    badge.addView(check, new FrameLayout.LayoutParams(dp(11), dp(11), Gravity.CENTER));
                    FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP | Gravity.END);
                    blp.topMargin = dp(8);
                    blp.rightMargin = dp(8);
                    tile.addView(badge, blp);
                }
                tile.setOnClickListener(v -> {
                    closeSheet();
                    wallpapers.pin(url, this::showWallpaper);
                });
                String thumbUrl = thumbs.get(i), fullUrl = url;
                new Thread(() -> {
                    Bitmap t = GitWallpapers.thumb(thumbUrl);
                    if (t == null) t = GitWallpapers.thumb(fullUrl);
                    Bitmap ft = t;
                    if (ft != null) main.post(() -> iv.setImageBitmap(ft));
                }, "wall-thumb").start();
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT);
                if (i > 0) lp.leftMargin = dp(10);
                strip.addView(tile, lp);
            }
            android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.addView(strip);
            box.addView(scroll, new LinearLayout.LayoutParams(Math.min(dp(5 * 146), dp(146) * urls.size()),
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            openSheet(box, 14);
        });
    }

    private void openInputs() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.addView(sheetTitle(R.string.console_inputs));
        TvInputManager tim = getSystemService(TvInputManager.class);
        if (tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (!info.isPassthroughInput()) continue;
                String id = info.getId();
                list.addView(sheetItem(String.valueOf(info.loadLabel(this)), v -> openInput(id)));
            }
        }
        if (!liveTvPkg.isEmpty()) {
            list.addView(sheetItem(getString(R.string.console_live_tv), v -> {
                Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
                if (i == null) i = getPackageManager().getLaunchIntentForPackage(liveTvPkg);
                if (i != null) start(i);
            }));
        }
        openSheet(list, -1);
    }

    private void openInput(String inputId) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!liveTvPkg.isEmpty()) {
            try {
                startActivity(new Intent(view).setPackage(liveTvPkg));
                return;
            } catch (ActivityNotFoundException | SecurityException ignored) {
            }
        }
        start(view);
    }

    private void openApps() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(sheetTitle(R.string.console_apps));
        TextView hint = new TextView(this);
        hint.setText(R.string.console_apps_hint);
        hint.setTextColor(0x99FFFFFF);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        hint.setTypeface(getResources().getFont(R.font.outfit_regular));
        hint.setPadding(dp(16), 0, 0, dp(10));
        box.addView(hint);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        List<String> home = homeApps();
        for (AppInfo a : loadApps()) {
            FrameLayout tile = focusable(new FrameLayout(this));
            tile.setPadding(dp(4), dp(4), dp(4), dp(4));
            ImageView iv = new ImageView(this);
            iv.setScaleType(a.tvBanner ? ImageView.ScaleType.CENTER_CROP : ImageView.ScaleType.CENTER_INSIDE);
            iv.setImageDrawable(a.banner);
            iv.setBackgroundColor(a.tvBanner ? a.bg : 0xFF1C1C1E);
            if (!a.tvBanner) iv.setPadding(dp(30), dp(14), dp(30), dp(14));
            iv.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override
                public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
                }
            });
            iv.setClipToOutline(true);
            tile.addView(iv, new FrameLayout.LayoutParams(dp(128), dp(72)));
            // A white check badge on the apps that are on Home; holding OK adds or removes the app.
            FrameLayout badge = new FrameLayout(this);
            badge.setBackground(circle(0xF5FFFFFF));
            badge.setElevation(dp(2));
            MorphIconView check = new MorphIconView(this);
            check.setTint(android.content.res.ColorStateList.valueOf(0xFF141416));
            check.setIcon(LucideIcons.CHECK, false);
            badge.addView(check, new FrameLayout.LayoutParams(dp(11), dp(11), Gravity.CENTER));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(18), dp(18), Gravity.TOP | Gravity.END);
            blp.topMargin = dp(8);
            blp.rightMargin = dp(8);
            badge.setVisibility(home.contains(a.pkg) ? View.VISIBLE : View.GONE);
            tile.addView(badge, blp);
            tile.setOnClickListener(v -> launch(a));
            tile.setOnLongClickListener(v -> {
                boolean on = badge.getVisibility() != View.VISIBLE;
                badge.setVisibility(on ? View.VISIBLE : View.GONE);
                setOnHome(a.pkg, on);
                return true;
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMargins(0, 0, (grid.getChildCount() % 5) == 4 ? 0 : dp(14), dp(14));
            grid.addView(tile, lp);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(grid);
        // Two rows show at once; more scroll inside the panel instead of growing it up the screen.
        int rows = (grid.getChildCount() + 4) / 5;
        box.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                rows > 2 ? dp(2 * (72 + 8 + 14)) : ViewGroup.LayoutParams.WRAP_CONTENT));
        openSheet(box, 14);
    }

    private void openPower() {
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.addView(sheetTitle(R.string.console_power));
        if (HomeKeyService.canOpenPowerMenu()) {
            list.addView(sheetItem(getString(R.string.console_power_menu), v -> {
                closeSheet();
                HomeKeyService.openPowerMenu();
            }));
        }
        Intent dream = new Intent(Intent.ACTION_MAIN).setClassName("com.android.systemui", "com.android.systemui.Somnambulator");
        if (getPackageManager().resolveActivity(dream, 0) != null) {
            list.addView(sheetItem(getString(R.string.console_screensaver), v -> {
                closeSheet();
                start(dream);
            }));
        }
        list.addView(sheetItem(getString(R.string.console_settings), v -> start(new Intent(Settings.ACTION_SETTINGS))));
        openSheet(list, -1);
    }

    private TextView sheetTitle(int text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xB3FFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setLetterSpacing(0.2f);
        t.setAllCaps(true);
        t.setTypeface(getResources().getFont(R.font.outfit_medium));
        t.setPadding(dp(16), 0, 0, dp(8));
        return t;
    }

    private View sheetItem(String label, View.OnClickListener click) {
        TextView t = focusable(new TextView(this));
        t.setText(label);
        t.setTextColor(onPill());
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(getResources().getFont(R.font.outfit_regular));
        t.setPadding(dp(16), dp(9), dp(28), dp(9));
        t.setOnClickListener(click);
        // As wide as the longest label in the list (a wrap_content list stretches these to its widest child).
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(2);
        t.setLayoutParams(lp);
        return t;
    }

    /** White, and the classic launcher's near-black (#141416) while the white focus pill sits under it. */
    private static android.content.res.ColorStateList onPill() {
        return new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xFF141416, Color.WHITE});
    }

    /** Focus is shown by the sheet's sliding pill, so the items themselves stay plain. */
    private <T extends View> T focusable(T v) {
        v.setFocusable(true);
        return v;
    }

    /**
     * Row and bar slide up and fade (250 ms), the sheet rises on a spring; the wallpaper stays put. A pill
     * ({@code pillRadiusDp}, -1 for fully round) glides behind whatever is focused inside it.
     */
    private void openSheet(View content, float pillRadiusDp) {
        sheet.removeAllViews();
        FrameLayout holder = new FrameLayout(this);
        // The pill is measured 0x0 in a wrap_content holder (it never sets the sheet's size), so it draws
        // past its own bounds over the items: the holder must not clip it.
        holder.setClipChildren(false);
        SlidingPill pill = new SlidingPill(this, 0, pillRadiusDp);
        holder.addView(pill, match());
        holder.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        if (content instanceof ViewGroup) pill.follow((ViewGroup) content);
        sheet.addView(holder);
        sheetOpen = true;
        for (View v : new View[]{band, barGlass, title}) {
            v.animate().cancel();
            v.animate().alpha(0f).translationY(-dp(12)).setDuration(250)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
        row.setFocusable(false);
        setBarFocusable(false);
        sheet.setVisibility(View.VISIBLE);
        sheet.setTranslationY(dp(380));
        springY(sheet, 0f);
        sheet.post(() -> {
            View first = content.focusSearch(View.FOCUS_DOWN);
            View target = firstFocusable(content);
            if (target != null) target.requestFocus();
            else if (first != null) first.requestFocus();
        });
    }

    private void closeSheet() {
        if (!sheetOpen) return;
        sheetOpen = false;
        SpringAnimation s = springY(sheet, dp(380));
        s.addEndListener((a, canceled, v, vel) -> {
            if (!sheetOpen) {
                sheet.setVisibility(View.GONE);
                sheet.removeAllViews();
            }
        });
        for (View v : new View[]{band, barGlass, title}) {
            v.animate().cancel();
            v.animate().alpha(1f).translationY(0f).setDuration(280)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }
        row.setFocusable(true);
        setBarFocusable(true);
        if (lastBarFocus != null) lastBarFocus.requestFocus();
        else row.requestFocus();
    }

    private void setBarFocusable(boolean on) {
        for (int i = 0; i < bar.getChildCount(); i++) bar.getChildAt(i).setFocusable(on);
    }

    private SpringAnimation springY(View v, float to) {
        SpringAnimation a = (SpringAnimation) v.getTag(R.id.spring_ty);
        if (a == null) {
            a = new SpringAnimation(v, DynamicAnimation.TRANSLATION_Y);
            a.setSpring(new SpringForce().setStiffness(380f).setDampingRatio(0.95f));
            v.setTag(R.id.spring_ty, a);
        }
        a.animateToFinalPosition(to);
        return a;
    }

    private View firstFocusable(View v) {
        if (v.isFocusable() && v.getVisibility() == View.VISIBLE) return v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                View f = firstFocusable(g.getChildAt(i));
                if (f != null) return f;
            }
        }
        return null;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // Up from the bar always lands on the row, whatever icon is under it.
        if (!sheetOpen && event.getAction() == KeyEvent.ACTION_DOWN
                && event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && bar.hasFocus()) {
            row.requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private int dp(float v) {
        return Math.round(v * density);
    }
}
