package com.hlw.gm;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.hlw.srv.Srv;

/** GM 面板：启动 Java 服务端 + 游戏画面上的功能菜单。 */
public final class Gm {

    private static final String TAG = "HLWGM";

    private Gm() {}

    public static void start(final Activity act) {
        try {
            Log.i(TAG, "start enter");
            act.runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Srv.start(act.getApplicationContext());
                        Log.i(TAG, "srv started: " + Srv.status());
                        addButton(act);
                    } catch (Throwable e) {
                        Log.e(TAG, "init fail", e);
                    }
                }
            });
        } catch (Throwable e) {
            Log.e(TAG, "start fail", e);
        }
    }

    private static android.view.WindowManager wm;
    private static TextView sBtn;
    private static View sPanel;

    private static void addButton(final Activity act) {
        // AIR 用 SurfaceView 渲染，普通 View 会被盖住 → 必须用系统悬浮窗（TYPE_APPLICATION_OVERLAY）
        act.getWindow().getDecorView().postDelayed(new Runnable() {
            public void run() {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= 23
                            && !android.provider.Settings.canDrawOverlays(act)) {
                        Log.i(TAG, "没有悬浮窗权限，跳转授权页");
                        try {
                            android.content.Intent it = new android.content.Intent(
                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    android.net.Uri.parse("package:" + act.getPackageName()));
                            it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                            act.startActivity(it);
                        } catch (Throwable e) { Log.e(TAG, "跳授权页失败", e); }
                        retryLater(act, 10);
                        return;
                    }
                    wm = (android.view.WindowManager) act.getSystemService(Activity.WINDOW_SERVICE);
                    sBtn = new TextView(act);
                    sBtn.setText("GM");
                    sBtn.setTextColor(Color.WHITE);
                    sBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    sBtn.setGravity(Gravity.CENTER);
                    sBtn.setBackgroundColor(0xDD1E88E5);
                    sBtn.setPadding(26, 12, 26, 12);
                    sBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) { panel(act); }
                    });
                    int flags = android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | android.view.WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
                    android.view.WindowManager.LayoutParams lp =
                            new android.view.WindowManager.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    android.os.Build.VERSION.SDK_INT >= 26
                                            ? android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                            : android.view.WindowManager.LayoutParams.TYPE_PHONE,
                                    flags, android.graphics.PixelFormat.TRANSLUCENT);
                    lp.gravity = Gravity.LEFT | Gravity.TOP;
                    lp.x = 16;
                    lp.y = 260;
                    wm.addView(sBtn, lp);
                    Log.i(TAG, "GM 悬浮窗已添加");
                } catch (Throwable e) {
                    Log.e(TAG, "addButton fail", e);
                }
            }
        }, 3000);
    }

    /** 授权页返回后自动重试（最多 n 次，每次 3 秒） */
    private static void retryLater(final Activity act, final int n) {
        if (n <= 0) return;
        act.getWindow().getDecorView().postDelayed(new Runnable() {
            public void run() {
                try {
                    android.provider.Settings.canDrawOverlays(act);
                    if (android.os.Build.VERSION.SDK_INT >= 23
                            && android.provider.Settings.canDrawOverlays(act)) {
                        Log.i(TAG, "已获得悬浮窗权限，重新挂载");
                        addButton(act);
                    } else {
                        retryLater(act, n - 1);
                    }
                } catch (Throwable e) { retryLater(act, n - 1); }
            }
        }, 3000);
    }

    /** GM 菜单：独立悬浮窗口，不依赖 Activity token（AIR 会重建 Activity，用 Dialog 必崩） */
    private static void panel(final Activity act) {
        try {
            if (sPanel != null && sPanel.getParent() != null) {
                wm.removeView(sPanel);
                sPanel = null;
                return;   // 再点一次收起
            }
            LinearLayout box = new LinearLayout(act);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (12 * act.getResources().getDisplayMetrics().density);
            box.setPadding(pad, pad, pad, pad);
            box.setBackgroundColor(0xF0101010);

            TextView title = new TextView(act);
            title.setText("GM 控制台");
            title.setTextColor(Color.WHITE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            box.addView(title);

            TextView st = new TextView(act);
            st.setText(Srv.status());
            st.setTextColor(0xFF88FF88);
            st.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            box.addView(st);

            addLabel(box, "切换出战角色（改完重开游戏）：");
            String[] names = {"大娃", "二娃", "三娃", "四娃", "五娃", "六娃", "七娃"};
            LinearLayout row = null;
            for (int i = 0; i < names.length; i++) {
                if (i % 4 == 0) { row = new LinearLayout(act); box.addView(row); }
                final int idx = i;
                Button b = new Button(act);
                b.setText(names[i]);
                b.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { toast(act, "已切换：" + Srv.setRole(idx) + "（请重开游戏）"); }
                });
                row.addView(b);
            }

            addLabel(box, "自定义属性（0 或留空=不改）：");
            final EditText a = num(act, box, "攻击");
            final EditText d = num(act, box, "防御");
            final EditText l = num(act, box, "生命");
            final EditText c = num(act, box, "暴击率 0~1");
            Button ap = new Button(act);
            ap.setText("应用属性");
            ap.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { toast(act, "已应用：" + Srv.setStats(v(a), v(d), v(l), v(c))); }
            });
            box.addView(ap);

            addLabel(box, "主线剧情：");
            LinearLayout r3 = new LinearLayout(act);
            Button rp = new Button(act);
            rp.setText("重放剧情");
            rp.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { toast(act, "已清零 " + Srv.resetStory() + " 关"); }
            });
            r3.addView(rp);
            Button rs = new Button(act);
            rs.setText("恢复全通关");
            rs.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { toast(act, "已恢复 " + Srv.restoreStory() + " 关"); }
            });
            r3.addView(rs);
            box.addView(r3);

            Button close = new Button(act);
            close.setText("关闭");
            close.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try { wm.removeView(sPanel); sPanel = null; } catch (Throwable ignored) {}
                }
            });
            box.addView(close);

            ScrollView sv = new ScrollView(act);
            sv.addView(box);

            int flags = android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            android.view.WindowManager.LayoutParams lp =
                    new android.view.WindowManager.LayoutParams(
                            (int) (300 * act.getResources().getDisplayMetrics().density),
                            (int) (420 * act.getResources().getDisplayMetrics().density),
                            android.os.Build.VERSION.SDK_INT >= 26
                                    ? android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                    : android.view.WindowManager.LayoutParams.TYPE_PHONE,
                            flags, android.graphics.PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.LEFT | Gravity.TOP;
            lp.x = 12;
            lp.y = 180;
            if (wm == null) wm = (android.view.WindowManager) act.getSystemService(Activity.WINDOW_SERVICE);
            wm.addView(sv, lp);
            sPanel = sv;
            Log.i(TAG, "GM 菜单已打开");
        } catch (Throwable e) {
            Log.e(TAG, "panel fail", e);
            toast(act, "打开失败：" + e);
        }
    }

    private static void addLabel(LinearLayout box, String t) {
        TextView v = new TextView(box.getContext());
        v.setText(t);
        v.setTextColor(0xFFBBBBBB);
        box.addView(v);
    }

    private static EditText num(Activity act, LinearLayout box, String hint) {
        EditText e = new EditText(act);
        e.setHint(hint);
        e.setTextColor(Color.WHITE);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        box.addView(e);
        return e;
    }

    private static double v(EditText e) {
        try { String s = e.getText().toString().trim(); return s.isEmpty() ? 0 : Double.parseDouble(s); }
        catch (Exception ex) { return 0; }
    }

    private static void toast(Activity a, String m) {
        Toast.makeText(a, m, Toast.LENGTH_SHORT).show();
    }
}
