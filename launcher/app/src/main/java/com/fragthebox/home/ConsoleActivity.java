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
    private GradientDrawable glow;
    private int glowColor = 0xFF5A6B8C;
    private android.animation.ValueAnimator glowAnim;
    private TextView title;
    private FrameLayout sheet;
    private View netDot;
    private GitWallpapers wallpapers;
    private String liveTvPkg = "";
    private boolean sheetOpen;
    private View lastBarFocus;

    private final Runnable rotate = new Runnable() {
        @Override
        public void run() {
            wallpapers.load(ConsoleActivity.this::showWallpaper);
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
        GlassPanel.setBackdrop(builtIn, dm.widthPixels, dm.heightPixels);

        liveTvPkg = findLiveTvPackage();
        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        refreshRow();
        row.requestFocus();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
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

        // Ambient glow in the focused app's colour, the classic launcher's "top shelf" light.
        glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.3f, 0.62f);
        glow.setGradientRadius(getResources().getDisplayMetrics().widthPixels * 0.62f);
        setGlow(glowColor);
        View glowView = new View(this);
        glowView.setBackground(glow);
        root.addView(glowView, match());

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0x00000000, 0x8C000000}));
        root.addView(shade, match());

        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("h:mm a");
        clock.setTextColor(Color.WHITE);
        clock.setAllCaps(true);
        clock.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18.5f);
        clock.setTypeface(getResources().getFont(R.font.outfit_regular));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        clp.topMargin = dp(32);
        clp.rightMargin = dp(60);
        root.addView(clock, clp);

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
        });

        // The focused app's name in large type above the row, as in the classic top shelf.
        title = new TextView(this);
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setTypeface(getResources().getFont(R.font.outfit_light));
        title.setSingleLine(true);
        title.setShadowLayer(dp(12), 0, dp(2), 0x66000000);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.START);
        tlp.leftMargin = dp(86);
        tlp.bottomMargin = dp(96 + 112 + 16);
        root.addView(title, tlp);

        FrameLayout.LayoutParams rlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(150), Gravity.BOTTOM);
        rlp.bottomMargin = dp(96);
        rlp.leftMargin = dp(86);
        root.addView(row, rlp);

        // The bar: a frosted glass pill, as wide as the reference's and a little taller so the icons breathe.
        bar = buildBar();
        barGlass = new GlassPanel(this, 23, 0x330B0D12);
        barGlass.addView(bar, match());
        barGlass.setPadding(dp(8), 0, dp(8), 0);
        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(470), dp(46), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        blp.bottomMargin = dp(22);
        root.addView(barGlass, blp);

        // Sheets float as a glass panel with margins on every side, not a slab glued to the screen's edge.
        sheet = new GlassPanel(this, 26, 0x8C0B0D12);
        sheet.setVisibility(View.GONE);
        sheet.setPadding(dp(46), dp(30), dp(46), dp(24));
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(330), Gravity.BOTTOM);
        slp.leftMargin = dp(40);
        slp.rightMargin = dp(40);
        slp.bottomMargin = dp(24);
        root.addView(sheet, slp);
        return root;
    }

    private LinearLayout buildBar() {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.HORIZONTAL);
        addBarIcon(b, R.drawable.ic_bar_inputs, R.string.console_inputs, false, v -> openInputs());
        Intent music = musicApp();
        if (music != null) addBarIcon(b, R.drawable.ic_bar_music, R.string.console_music, false, v -> start(music));
        Intent notifications = notificationsPanel();
        if (notifications != null) {
            addBarIcon(b, R.drawable.ic_bar_bell, R.string.console_notifications, false, v -> start(notifications));
        }
        View home = addBarIcon(b, R.drawable.ic_bar_home, R.string.console_home, true, v -> {
            row.home();
            row.requestFocus();
        });
        addBarIcon(b, R.drawable.ic_bar_apps, R.string.console_apps, false, v -> openApps());
        View net = addBarIcon(b, R.drawable.ic_bar_wifi, R.string.console_network, false,
                v -> start(new Intent(Settings.ACTION_WIFI_SETTINGS)));
        netDot = net.findViewWithTag("dot");
        addBarIcon(b, R.drawable.ic_bar_power, R.string.console_power, false, v -> openPower());
        row.setNextFocusDownId(home.getId());
        return b;
    }

    private View addBarIcon(LinearLayout b, int icon, int label, boolean selected, View.OnClickListener click) {
        FrameLayout cell = new FrameLayout(this);
        cell.setId(View.generateViewId());
        cell.setFocusable(true);
        cell.setContentDescription(getString(label));
        cell.setOnClickListener(click);
        cell.setNextFocusUpId(row.getId());
        cell.setOnFocusChangeListener((v, has) -> {
            if (has) lastBarFocus = v;
        });

        View halo = new View(this);
        halo.setDuplicateParentStateEnabled(true);
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focusedHalo = circle(0x40FFFFFF);
        focusedHalo.setStroke(Math.max(1, dp(0.8f)), 0xB3FFFFFF);
        states.addState(new int[]{android.R.attr.state_focused}, focusedHalo);
        states.addState(new int[]{}, circle(selected ? 0x26FFFFFF : 0x00FFFFFF));
        halo.setBackground(states);
        cell.addView(halo, new FrameLayout.LayoutParams(dp(36), dp(36), Gravity.CENTER));

        ImageView iv = new ImageView(this);
        iv.setImageResource(icon);
        cell.addView(iv, new FrameLayout.LayoutParams(dp(icon == R.drawable.ic_bar_wifi ? 21 : 17),
                dp(icon == R.drawable.ic_bar_wifi ? 21 : 17), Gravity.CENTER));
        if (icon == R.drawable.ic_bar_wifi) {
            View dot = new View(this);
            dot.setTag("dot");
            GradientDrawable g = circle(0xFF3DDC84);
            g.setStroke(dp(1), 0xFF0B0D12);
            dot.setBackground(g);
            FrameLayout.LayoutParams dlp = new FrameLayout.LayoutParams(dp(6), dp(6), Gravity.CENTER);
            dlp.leftMargin = dp(15);
            dlp.topMargin = dp(15);
            cell.addView(dot, dlp);
        }
        b.addView(cell, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        return cell;
    }

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

        String name = item.title;
        if (name.contentEquals(title.getText())) return;
        title.animate().cancel();
        title.animate().alpha(0f).translationY(dp(6)).setDuration(110).withEndAction(() -> {
            title.setText(name);
            title.setTranslationY(-dp(6));
            title.animate().alpha(1f).translationY(0f).setDuration(260)
                    .setInterpolator(new DecelerateInterpolator(1.6f)).start();
        }).start();
    }

    private void setGlow(int color) {
        glowColor = color;
        glow.setColors(new int[]{(color & 0x00FFFFFF) | 0x8C000000, (color & 0x00FFFFFF) | 0x26000000, 0x00000000});
    }

    // ---- wallpaper ---------------------------------------------------------------------------------

    private void showWallpaper(Bitmap b) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        GlassPanel.setBackdrop(b, dm.widthPixels, dm.heightPixels);
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
                for (AppInfo a : apps) {
                    ConsoleRow.Item it = new ConsoleRow.Item();
                    it.title = a.label;
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
        if (netDot != null) netDot.setVisibility(online ? View.VISIBLE : View.INVISIBLE);
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
        openSheet(list);
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
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(5);
        for (AppInfo a : loadApps()) {
            FrameLayout tile = outlinedFocus(new FrameLayout(this));
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
            tile.setOnClickListener(v -> launch(a));
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMargins(0, 0, dp(14), dp(14));
            grid.addView(tile, lp);
        }
        ScrollView scroll = new ScrollView(this);
        scroll.addView(grid);
        box.addView(scroll);
        openSheet(box);
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
        openSheet(list);
    }

    private TextView sheetTitle(int text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(0xB3FFFFFF);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        t.setLetterSpacing(0.2f);
        t.setAllCaps(true);
        t.setTypeface(getResources().getFont(R.font.outfit_medium));
        t.setPadding(0, 0, 0, dp(14));
        return t;
    }

    private View sheetItem(String label, View.OnClickListener click) {
        TextView t = outlinedFocus(new TextView(this));
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        t.setTypeface(getResources().getFont(R.font.outfit_regular));
        t.setPadding(dp(14), dp(10), dp(14), dp(10));
        t.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(360), ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        t.setLayoutParams(lp);
        return t;
    }

    /** The reference's list focus: a thin white outline that moves between items, no scaling. */
    private <T extends View> T outlinedFocus(T v) {
        v.setFocusable(true);
        StateListDrawable s = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setStroke(dp(1.5f), Color.WHITE);
        on.setColor(0x14FFFFFF);
        on.setCornerRadius(dp(6));
        GradientDrawable off = new GradientDrawable();
        off.setColor(0x00000000);
        off.setCornerRadius(dp(6));
        s.addState(new int[]{android.R.attr.state_focused}, on);
        s.addState(new int[]{}, off);
        v.setBackground(s);
        return v;
    }

    /** Row and bar slide up and fade (250 ms), the sheet rises on a spring; the wallpaper stays put. */
    private void openSheet(View content) {
        sheet.removeAllViews();
        sheet.addView(content);
        sheetOpen = true;
        for (View v : new View[]{row, barGlass, title}) {
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
        for (View v : new View[]{row, barGlass, title}) {
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
