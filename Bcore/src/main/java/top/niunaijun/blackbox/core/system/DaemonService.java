package top.niunaijun.blackbox.core.system;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.util.List;
import java.util.Locale;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.utils.compat.BuildCompat;

public class DaemonService extends Service {
    public static final String TAG = "DaemonService";
    public static final String ACTION_QUIT = "top.niunaijun.blackbox.action.QUIT_ALL";

    private static final int NOTIFY_ID = BlackBoxCore.getHostPkg().hashCode();
    private static final String CHANNEL_ID = "blackbox_daemon_channel";
    private static final long POLL_MS = 2500L;
    private static final int IMPORTANCE_CACHED_VALUE = 400;

    private static final String HOST_PREFS = "settings";
    private static final String HOST_PREF_LANG = "lang";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean foregroundShown;
    private String shownKey = "";

    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            refresh();
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "DaemonService onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "DaemonService onStartCommand");

        if (intent != null && ACTION_QUIT.equals(intent.getAction())) {
            quitAll();
            return START_NOT_STICKY;
        }

        enterForeground();
        refresh();

        handler.removeCallbacks(poll);
        handler.postDelayed(poll, POLL_MS);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "DaemonService onDestroy");
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void refresh() {
        boolean running = hasRunningClones();
        if (running) {
            if (!foregroundShown || !currentKey().equals(shownKey)) {
                enterForeground();
            }
        } else if (foregroundShown) {
            leaveForeground();
        }
    }

    private void enterForeground() {
        try {
            Context lc = localizedContext();
            ensureChannel(lc);
            startForeground(NOTIFY_ID, buildNotification(lc));
            foregroundShown = true;
            shownKey = currentKey();
        } catch (Throwable t) {
            Log.e(TAG, "startForeground failed: " + t.getMessage(), t);
        }
    }

    private void leaveForeground() {
        try {
            if (Build.VERSION.SDK_INT >= 24) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE);
            } else {
                stopForeground(true);
            }
        } catch (Throwable t) {
            Log.e(TAG, "stopForeground failed: " + t.getMessage(), t);
        }
        foregroundShown = false;
    }

    private void quitAll() {
        try {
            handler.removeCallbacksAndMessages(null);
            leaveForeground();
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.cancel(NOTIFY_ID);
            stopSelf();

            int me = Process.myPid();
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> list = am != null ? am.getRunningAppProcesses() : null;
            if (list != null) {
                for (ActivityManager.RunningAppProcessInfo p : list) {
                    if (p.pid != me) Process.killProcess(p.pid);
                }
            }
        } catch (Throwable t) {
            Log.e(TAG, "quitAll failed: " + t.getMessage(), t);
        }
        Process.killProcess(Process.myPid());
    }

    private boolean hasRunningClones() {
        return hasCloneTask() || hasActiveCloneProcess();
    }

    private boolean hasCloneTask() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.AppTask> tasks = am != null ? am.getAppTasks() : null;
            if (tasks == null) return false;
            for (ActivityManager.AppTask t : tasks) {
                ActivityManager.RecentTaskInfo info = t.getTaskInfo();
                if (info == null || info.baseIntent == null) continue;
                ComponentName c = info.baseIntent.getComponent();
                if (c != null && c.getClassName().contains(".proxy.ProxyActivity")) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private boolean hasActiveCloneProcess() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> list = am != null ? am.getRunningAppProcesses() : null;
            if (list == null) return false;
            String prefix = getPackageName() + ":p";
            for (ActivityManager.RunningAppProcessInfo p : list) {
                String n = p.processName;
                if (n == null || !n.startsWith(prefix) || n.length() == prefix.length()) continue;
                if (!isDigits(n.substring(prefix.length()))) continue;
                if (p.importance < IMPORTANCE_CACHED_VALUE) return true;
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private static boolean isDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) return false;
        }
        return true;
    }

    private Notification buildNotification(Context lc) {
        int icon = lc.getResources().getIdentifier("ic_stat_clone", "drawable", getPackageName());
        if (icon == 0) icon = android.R.drawable.ic_dialog_info;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(str(lc, "daemon_notif_title", "eFootClones is running in the background"))
                .setContentText(str(lc, "daemon_notif_text", "Tap to quit the app"))
                .setSmallIcon(icon)
                .setColor(0xFF7C5CFF)
                .setContentIntent(quitIntent())
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setOngoing(true)
                .setAutoCancel(false)
                .build();
    }

    private PendingIntent quitIntent() {
        Intent i = new Intent(this, DaemonService.class).setAction(ACTION_QUIT);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 23) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, 0, i, flags);
    }

    private void ensureChannel(Context lc) {
        if (!BuildCompat.isOreo()) return;
        try {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID,
                    str(lc, "daemon_notif_channel", "Background service"),
                    NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(str(lc, "daemon_notif_channel_desc", "Keeps the engine alive while clones run"));
            ch.setShowBadge(false);
            ch.setSound(null, null);
            ch.enableVibration(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        } catch (Throwable t) {
            Log.e(TAG, "Failed to create notification channel: " + t.getMessage(), t);
        }
    }

    @SuppressWarnings("deprecation")
    private Locale hostLocale() {
        int mode = 0;
        try {
            SharedPreferences sp = getSharedPreferences(HOST_PREFS, Context.MODE_MULTI_PROCESS);
            mode = sp.getInt(HOST_PREF_LANG, 0);
        } catch (Throwable ignored) {
        }
        if (mode == 1) return new Locale("ar");
        if (mode == 2) return Locale.ENGLISH;
        Configuration cfg = Resources.getSystem().getConfiguration();
        Locale l = Build.VERSION.SDK_INT >= 24 ? cfg.getLocales().get(0) : cfg.locale;
        return "ar".equals(l.getLanguage()) ? new Locale("ar") : Locale.ENGLISH;
    }

    private String currentKey() {
        return hostLocale().getLanguage();
    }

    private Context localizedContext() {
        try {
            Configuration cfg = new Configuration(getResources().getConfiguration());
            cfg.setLocale(hostLocale());
            return createConfigurationContext(cfg);
        } catch (Throwable t) {
            return this;
        }
    }

    private String str(Context c, String name, String fallback) {
        try {
            int id = c.getResources().getIdentifier(name, "string", getPackageName());
            if (id != 0) return c.getString(id);
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    public static class DaemonInnerService extends Service {
        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            Log.i(TAG, "DaemonInnerService -> onStartCommand");
            stopSelf();
            return START_NOT_STICKY;
        }

        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }
}
