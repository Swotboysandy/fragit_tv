package com.fragthebox.home;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * The panel the remote's Settings button opens when {@code settings_key_panel} is on (HomeKeyService):
 * a few glass rows under the Settings pill, over Home or whatever app is playing. Translucent and in a
 * task of its own, so the app behind keeps running and Back returns to it. Pressing Settings again
 * closes the panel.
 */
public final class QuickSettingsActivity extends Activity {
    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(LauncherLocale.wrap(base));   // locale
    }


    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_quick_settings);
        if (getResources().getBoolean(R.bool.rtl_menu_bar)) {
            View panel = findViewById(R.id.panel);
            android.widget.FrameLayout.LayoutParams position =
                    (android.widget.FrameLayout.LayoutParams) panel.getLayoutParams();
            position.gravity = android.view.Gravity.TOP | android.view.Gravity.LEFT;
            int inset = position.getMarginEnd();
            position.setMarginEnd(0);
            position.setMargins(inset, position.topMargin, 0, position.bottomMargin);
            panel.setLayoutParams(position);
        }
        findViewById(R.id.scrim).setOnClickListener(v -> finish());
        LinearLayout rows = findViewById(R.id.rows);
        // frag: the everyday settings first, each with what it is set to now; the full TV settings last.
        addRow(rows, R.drawable.ic_wifi, getString(R.string.quick_network), wifiName(),
                SystemSettings.intent(this, Settings.ACTION_WIFI_SETTINGS));
        addRow(rows, R.drawable.ic_bluetooth, getString(R.string.quick_bluetooth), bluetoothState(), bluetoothScreen());
        addRow(rows, R.drawable.ic_brightness, getString(R.string.brightness), brightnessState(),
                new Intent(this, BrightnessActivity.class));
        addRow(rows, R.drawable.ic_picture, getString(R.string.quick_picture_sound), getString(R.string.quick_picture_sound_hint),
                tvPictureMenu());
        addRow(rows, R.drawable.ic_sound, getString(R.string.quick_sound), null,
                SystemSettings.intent(this, Settings.ACTION_SOUND_SETTINGS));
        addRow(rows, R.drawable.ic_apps, getString(R.string.quick_apps), getString(R.string.quick_apps_hint),
                SystemSettings.intent(this, Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS));
        // Through Home, so it rebuilds with the new choices when Launcher settings closes.
        addRow(rows, R.drawable.ic_home_settings, getString(R.string.launcher_settings), getString(R.string.quick_home_hint),
                new Intent(this, MainActivity.class).setAction(MainActivity.ACTION_LAUNCHER_SETTINGS));
        addRow(rows, R.drawable.ic_settings, getString(R.string.quick_all_settings), null,
                SystemSettings.intent(this, Settings.ACTION_SETTINGS));
        rows.getChildAt(0).requestFocus();
    }

    /** Settings pressed again while the panel is up. */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        finish();
    }

    /** Anything else taking the screen (Home, an app, a screensaver) closes the panel. */
    @Override
    protected void onPause() {
        super.onPause();
        finish();
        overridePendingTransition(0, 0);
    }

    /** The network the TV is on, or null when it can't be read (location permission off, Ethernet, offline). */
    private String wifiName() {
        try {
            android.net.wifi.WifiManager wm = getApplicationContext().getSystemService(android.net.wifi.WifiManager.class);
            String ssid = wm == null ? null : wm.getConnectionInfo().getSSID();
            if (ssid != null && !ssid.contains("unknown ssid")) return ssid.replace("\"", "");
            // No name without location access: say what kind of connection it is instead.
            android.net.ConnectivityManager cm = getSystemService(android.net.ConnectivityManager.class);
            android.net.NetworkCapabilities caps = cm == null || cm.getActiveNetwork() == null ? null
                    : cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (caps == null) return getString(R.string.quick_not_connected);
            if (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET)) return getString(R.string.quick_ethernet);
            return getString(R.string.quick_connected);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String bluetoothState() {
        try {
            android.bluetooth.BluetoothAdapter a = android.bluetooth.BluetoothAdapter.getDefaultAdapter();
            if (a == null) return null;
            return getString(a.isEnabled() ? R.string.quick_on : R.string.quick_off);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String brightnessState() {
        int level = Brightness.level(this);
        return level == Brightness.AUTO ? getString(R.string.quick_auto) : level + " %";
    }

    /** Android TV has no BLUETOOTH_SETTINGS screen; its "add accessory" page pairs remotes, headphones and speakers. */
    private Intent bluetoothScreen() {
        Intent pair = new Intent("com.google.android.intent.action.CONNECT_INPUT").setPackage("com.android.tv.settings");
        if (getPackageManager().resolveActivity(pair, 0) != null) return pair;
        return SystemSettings.intent(this, Settings.ACTION_BLUETOOTH_SETTINGS);
    }

    /** TCL's own picture and sound menu (where the backlight lives), else Android's display settings. */
    private Intent tvPictureMenu() {
        Intent tcl = new Intent().setComponent(new android.content.ComponentName("com.tcl.settings", "com.tcl.settings.MainActivity"));
        if (getPackageManager().resolveActivity(tcl, 0) != null) return tcl;
        return SystemSettings.intent(this, Settings.ACTION_DISPLAY_SETTINGS);
    }

    private void addRow(LinearLayout rows, int icon, String title, String status, Intent target) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(icon);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        row.findViewById(R.id.dot).setVisibility(View.GONE);
        TextView hint = row.findViewById(R.id.status);
        if (status != null) hint.setText(status);
        else hint.setVisibility(View.GONE);
        row.setOnClickListener(v -> {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException | SecurityException e) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
            finish();
        });
        rows.addView(row);
    }
}
