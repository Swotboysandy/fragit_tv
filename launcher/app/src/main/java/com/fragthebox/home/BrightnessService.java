package com.fragthebox.home;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;

/**
 * Holds the frag brightness layer: one full-screen window of black, with amber folded in for warmth, over every
 * app. It never takes focus or keys. In Auto it re-checks the time of day every few minutes; at full brightness
 * with no warmth it removes the window and stops.
 */
public class BrightnessService extends Service {

    private static final String CHANNEL = "brightness";
    private static final long AUTO_CHECK_MS = 5 * 60 * 1000L;

    private final Handler main = new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View layer;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            apply();
            if (Brightness.level(BrightnessService.this) == Brightness.AUTO) main.postDelayed(this, AUTO_CHECK_MS);
        }
    };

    /** Applies the current setting: starts the service when the layer is needed, stops it when it isn't. */
    static void update(Context ctx) {
        Intent i = new Intent(ctx, BrightnessService.class);
        if (Brightness.idle(ctx)) {
            ctx.stopService(i);
        } else if (Settings.canDrawOverlays(ctx)) {
            ctx.startForegroundService(i);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        wm = getSystemService(WindowManager.class);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.brightness),
                NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_brightness)
                .setContentTitle(getString(R.string.brightness))
                .build();
        startForeground(1, n);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        main.removeCallbacks(tick);
        main.post(tick);
        return START_STICKY;
    }

    private void apply() {
        if (Brightness.idle(this) || !Settings.canDrawOverlays(this)) {
            removeLayer();
            stopSelf();
            return;
        }
        float[] now = Brightness.now(this);
        if (layer == null) {
            layer = new View(this);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            wm.addView(layer, lp);
        }
        // Black for the missing light, with an amber cast for warmth; one colour, so the picture keeps its contrast.
        float dark = 1f - now[0];
        float warm = now[1];
        float alpha = Math.min(0.92f, dark + warm * 0.35f);
        int r = Math.round(255 * warm / Math.max(alpha, 0.001f) * 0.35f);
        int g = Math.round(140 * warm / Math.max(alpha, 0.001f) * 0.35f);
        int color = Color.argb(Math.round(alpha * 255), Math.min(255, r), Math.min(255, g), 0);
        layer.setBackgroundColor(color);
    }

    private void removeLayer() {
        if (layer != null) {
            try {
                wm.removeView(layer);
            } catch (RuntimeException ignored) {
            }
            layer = null;
        }
    }

    @Override
    public void onDestroy() {
        main.removeCallbacks(tick);
        removeLayer();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
