package com.huluwa.localserver;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

/** 前台服务：把 Python 服务端跑起来，避免被系统回收。 */
public class ServerService extends Service {

    private static final String TAG = "HuluwaServer";
    private static final String CHANNEL = "huluwa_server";

    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundNotice();
        try {
            if (!Python.isStarted()) {
                Python.start(new AndroidPlatform(this));
            }
            Python py = Python.getInstance();
            // 数据目录 = 应用私有目录，存档写这里（APK 内资源只读）
            String dataDir = getFilesDir().getAbsolutePath();
            py.getModule("server").callAttr("start_all", dataDir);
            Log.i(TAG, "python server started, dataDir=" + dataDir);
        } catch (Throwable t) {
            Log.e(TAG, "start python server failed", t);
        }
    }

    private void startForegroundNotice() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL, "单机服务端", NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
            Notification n = new Notification.Builder(this, CHANNEL)
                    .setContentTitle("单机服务端运行中")
                    .setContentText("HTTP 8080 / TCP 9000")
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .build();
            startForeground(1, n);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
