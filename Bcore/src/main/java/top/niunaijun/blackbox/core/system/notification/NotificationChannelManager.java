package top.niunaijun.blackbox.core.system.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;

import androidx.annotation.RequiresApi;

import java.util.Locale;

import top.niunaijun.blackbox.BlackBoxCore;

/**
 * Default channel for notifications of apps running inside the clones.
 * Its name comes from the host app strings (notif_channel_apps / notif_channel_apps_desc),
 * in the host app language (SharedPreferences "settings" / "lang": 0 system, 1 ar, 2 en).
 */
public class NotificationChannelManager {
    private final static NotificationChannelManager sManager = new NotificationChannelManager();

    public static NotificationChannel APP_CHANNEL;

    public static NotificationChannelManager get() {
        return sManager;
    }

    public NotificationChannelManager() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            registerAppChannel();
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    private void registerAppChannel() {
        Context ctx = BlackBoxCore.getContext();
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        String CHANNEL_ONE_ID = ctx.getPackageName();
        APP_CHANNEL = new NotificationChannel(CHANNEL_ONE_ID,
                hostText(ctx, "notif_channel_apps", "Notifications from cloned apps"),
                NotificationManager.IMPORTANCE_HIGH);
        APP_CHANNEL.setDescription(hostText(ctx, "notif_channel_apps_desc",
                "Notifications sent by the apps running inside your clones"));
        APP_CHANNEL.enableLights(true);
        APP_CHANNEL.setLightColor(Color.RED);
        APP_CHANNEL.setShowBadge(true);
        APP_CHANNEL.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        nm.createNotificationChannel(APP_CHANNEL);
    }

    @SuppressWarnings("deprecation")
    private static String hostText(Context c, String name, String fallback) {
        try {
            int mode = 0;
            try {
                mode = c.getSharedPreferences("settings", Context.MODE_MULTI_PROCESS).getInt("lang", 0);
            } catch (Throwable ignored) {
            }
            Locale loc;
            if (mode == 1) {
                loc = new Locale("ar");
            } else if (mode == 2) {
                loc = Locale.ENGLISH;
            } else {
                Configuration sys = Resources.getSystem().getConfiguration();
                Locale l = Build.VERSION.SDK_INT >= 24 ? sys.getLocales().get(0) : sys.locale;
                loc = "ar".equals(l.getLanguage()) ? new Locale("ar") : Locale.ENGLISH;
            }
            Configuration cfg = new Configuration(c.getResources().getConfiguration());
            cfg.setLocale(loc);
            Context lc = c.createConfigurationContext(cfg);
            int id = lc.getResources().getIdentifier(name, "string", c.getPackageName());
            if (id != 0) return lc.getString(id);
        } catch (Throwable ignored) {
        }
        return fallback;
    }
}
