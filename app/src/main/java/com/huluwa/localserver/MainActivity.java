package com.huluwa.localserver;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * 少年葫芦娃 · 单机服务端
 *
 * 这个 APK 里打包了一份 Python 服务端（Chaquopy），启动后在手机本地监听：
 *   HTTP 8080  —— 服务器列表 / 会话 / 配置 / 进关卡 / 战斗结算
 *   TCP  9000  —— 游戏二进制协议
 * 游戏本体连的就是 127.0.0.1，所以同机运行即可。
 */
public class MainActivity extends AppCompatActivity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("少年葫芦娃 · 单机服务端");
        title.setTextSize(20);
        root.addView(title);

        status = new TextView(this);
        status.setText("状态：未启动");
        status.setTextSize(14);
        status.setPadding(0, pad, 0, pad);
        root.addView(status);

        Button start = new Button(this);
        start.setText("启动服务端");
        start.setOnClickListener(v -> startServer());
        root.addView(start);

        Button stop = new Button(this);
        stop.setText("停止服务端");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, ServerService.class));
            status.setText("状态：已停止");
        });
        root.addView(stop);

        setContentView(root);

        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }

        startServer();
    }

    private void startServer() {
        Intent i = new Intent(this, ServerService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        status.setText("状态：启动中…\nHTTP 8080 / TCP 9000\n（保持本应用后台运行）");
    }
}
