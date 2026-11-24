package com.huluwa.localserver;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

/**
 * 少年葫芦娃 · 单机服务端
 *
 * 内置 Python 服务端（Chaquopy），本机监听 HTTP 8080 / TCP 9000。
 * 附带「角色切换」面板：点一下即可换上指定的葫芦娃，并自动重启游戏生效。
 */
public class MainActivity extends Activity {

    private static final String GAME_PKG = "com.ada.snhlw.android.qihoo";
    private static final String[] ROLES = {"大娃", "二娃", "三娃", "四娃", "五娃", "六娃", "七娃"};

    private TextView status;
    private LinearLayout roleRow;
    private EditText inAtk, inDef, inLife, inCrit, inCritF;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("少年葫芦娃 · 单机服务端");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        root.addView(title);

        status = new TextView(this);
        status.setText("状态：未启动");
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        status.setPadding(0, pad, 0, pad);
        root.addView(status);

        // ── 服务端控制 ──
        LinearLayout srvRow = new LinearLayout(this);
        srvRow.setOrientation(LinearLayout.HORIZONTAL);

        Button start = new Button(this);
        start.setText("启动服务端");
        start.setOnClickListener(v -> startServer());
        srvRow.addView(start, lp());

        Button stop = new Button(this);
        stop.setText("停止");
        stop.setOnClickListener(v -> {
            stopService(new Intent(this, ServerService.class));
            status.setText("状态：已停止");
        });
        srvRow.addView(stop, lp());
        root.addView(srvRow);

        // ── 角色切换 ──
        TextView roleTip = new TextView(this);
        roleTip.setText("\n选择出战角色（点击后自动重启游戏）：");
        roleTip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(roleTip);

        roleRow = new LinearLayout(this);
        roleRow.setOrientation(LinearLayout.VERTICAL);
        root.addView(roleRow);
        buildRoleButtons();

        // ── 剧情重放 ──
        TextView storyTip = new TextView(this);
        storyTip.setText("\n主线剧情：");
        storyTip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(storyTip);

        LinearLayout storyRow = new LinearLayout(this);
        storyRow.setOrientation(LinearLayout.HORIZONTAL);

        Button replay = new Button(this);
        replay.setText("重放主线剧情");
        replay.setOnClickListener(v -> callStory("reset_story", "剧情已重置，正在重启游戏…"));
        storyRow.addView(replay, lp());

        Button restore = new Button(this);
        restore.setText("恢复全通关");
        restore.setOnClickListener(v -> callStory("restore_story", "已恢复全通关，正在重启游戏…"));
        storyRow.addView(restore, lp());
        root.addView(storyRow);

        Button launch = new Button(this);
        launch.setText("启动 / 重启游戏");
        launch.setOnClickListener(v -> restartGame());
        root.addView(launch);

        // ── 自定义属性 ──
        TextView stTip = new TextView(this);
        stTip.setText("\n自定义属性（全部角色通用，留空=不改）：");
        stTip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        root.addView(stTip);

        inAtk = numField(root, "攻击力，例如 99999");
        inDef = numField(root, "防御力，例如 88888");
        inLife = numField(root, "生命值，例如 500000");
        inCrit = numField(root, "暴击率 0~1，例如 0.5");
        inCritF = numField(root, "暴击伤害倍率，例如 2.0");

        LinearLayout stRow = new LinearLayout(this);
        stRow.setOrientation(LinearLayout.HORIZONTAL);

        Button apply = new Button(this);
        apply.setText("应用属性");
        apply.setOnClickListener(v -> applyStats());
        stRow.addView(apply, lp());

        Button clear = new Button(this);
        clear.setText("清除自定义");
        clear.setOnClickListener(v -> callSimple("clear_stats", "已清除自定义属性"));
        stRow.addView(clear, lp());
        root.addView(stRow);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
        startServer();
        refreshRoleLabel();
    }

    private void buildRoleButtons() {
        roleRow.removeAllViews();
        LinearLayout row = null;
        for (int i = 0; i < ROLES.length; i++) {
            if (i % 4 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                roleRow.addView(row);
            }
            final int idx = i;
            Button b = new Button(this);
            b.setText(ROLES[i]);
            b.setOnClickListener(v -> pickRole(idx));
            row.addView(b, lp());
        }
    }

    private void pickRole(int idx) {
        try {
            if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
            PyObject name = Python.getInstance().getModule("server").callAttr("set_role", idx);
            Toast.makeText(this, "已切换到 " + name + "，正在重启游戏…", Toast.LENGTH_SHORT).show();
            status.setText("当前角色：" + name + "\n正在重启游戏…");
            restartGame();
        } catch (Throwable t) {
            status.setText("切换失败：" + t);
        }
    }

    private EditText numField(LinearLayout parent, String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        parent.addView(e);
        return e;
    }

    private void applyStats() {
        try {
            if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
            PyObject r = Python.getInstance().getModule("server").callAttr("set_stats",
                    num(inAtk), num(inDef), num(inLife), num(inCrit), num(inCritF));
            Toast.makeText(this, "属性已应用，正在重启游戏…", Toast.LENGTH_SHORT).show();
            status.setText("属性已应用：" + r + "\n正在重启游戏…");
            restartGame();
        } catch (Throwable t) {
            status.setText("应用失败：" + t);
        }
    }

    private double num(EditText e) {
        try {
            String s = e.getText().toString().trim();
            return s.isEmpty() ? 0 : Double.parseDouble(s);
        } catch (Exception ex) {
            return 0;
        }
    }

    private void callSimple(String fn, String msg) {
        try {
            if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
            Python.getInstance().getModule("server").callAttr(fn);
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
            status.setText(msg);
        } catch (Throwable t) {
            status.setText("操作失败：" + t);
        }
    }

    private void callStory(String fn, String msg) {
        try {
            if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
            PyObject r = Python.getInstance().getModule("server").callAttr(fn);
            Toast.makeText(this, msg + "（共 " + r + " 关）", Toast.LENGTH_SHORT).show();
            status.setText(msg + "\n共 " + r + " 关");
            restartGame();
        } catch (Throwable t) {
            status.setText("操作失败：" + t);
        }
    }

    private void refreshRoleLabel() {
        try {
            if (!Python.isStarted()) Python.start(new AndroidPlatform(this));
            PyObject cur = Python.getInstance().getModule("server").callAttr("get_role");
            String key = cur.toString();
            int i = 0;
            try { i = Integer.parseInt(key.replace("role", "")); } catch (Exception ignored) {}
            status.setText("当前角色：" + (i >= 0 && i < ROLES.length ? ROLES[i] : key)
                    + "\nHTTP 8080 / TCP 9000");
        } catch (Throwable ignored) {
        }
    }

    private void startServer() {
        Intent i = new Intent(this, ServerService.class);
        if (android.os.Build.VERSION.SDK_INT >= 26) startForegroundService(i);
        else startService(i);
        refreshRoleLabel();
    }

    /** 结束游戏后台进程再拉起，让它重新登录（新的 sltRole 才会生效）。 */
    private void restartGame() {
        try {
            ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) am.killBackgroundProcesses(GAME_PKG);
        } catch (Throwable ignored) {
        }
        Intent launch = getPackageManager().getLaunchIntentForPackage(GAME_PKG);
        if (launch == null) {
            Toast.makeText(this, "没有找到游戏本体", Toast.LENGTH_LONG).show();
            return;
        }
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        try {
            startActivity(launch);
        } catch (Throwable t) {
            Toast.makeText(this, "启动失败：" + t, Toast.LENGTH_LONG).show();
        }
    }

    private LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(2, 2, 2, 2);
        return p;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshRoleLabel();
    }
}
