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

    /** GM 菜单：紧凑单屏布局（不依赖滚动），带关闭按钮 */
    private static void panel(final Activity act) {
        try {
            if (sPanel != null && sPanel.getParent() != null) {
                wm.removeView(sPanel); sPanel = null; return;
            }
            float dp = act.getResources().getDisplayMetrics().density;
            LinearLayout box = new LinearLayout(act);
            box.setOrientation(LinearLayout.VERTICAL);
            box.setBackgroundColor(0xF2121212);
            box.setPadding((int)(8*dp), (int)(6*dp), (int)(8*dp), (int)(6*dp));

            // ── 标题行 + 关闭 ──
            LinearLayout top = new LinearLayout(act);
            top.setOrientation(LinearLayout.HORIZONTAL);
            TextView title = new TextView(act);
            title.setText("GM 控制台");
            title.setTextColor(Color.WHITE);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            title.setLayoutParams(new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            top.addView(title);
            Button x = new Button(act);
            x.setText("关闭");
            x.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            x.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    try { wm.removeView(sPanel); sPanel = null; } catch (Throwable ignored) {}
                }
            });
            top.addView(x);
            box.addView(top);

            TextView st = new TextView(act);
            st.setText(Srv.status());
            st.setTextColor(0xFF88FF88);
            st.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            box.addView(st);

            // ── 角色：7 个一行 ──
            addLabel(box, "出战角色（改完重开游戏）：");
            LinearLayout row = new LinearLayout(act);
            String[] names = {"大娃", "二娃", "三娃", "四娃", "五娃", "六娃", "七娃"};
            for (int i = 0; i < names.length; i++) {
                final int idx = i;
                Button b = new Button(act);
                b.setText(names[i]);
                b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
                b.setPadding(0, 0, 0, 0);
                b.setMinimumWidth(0);
                b.setLayoutParams(new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
                b.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { toast(act, "已切换：" + Srv.setRole(idx) + "（请重开游戏）"); }
                });
                row.addView(b);
            }
            box.addView(row);

            // ── 属性：4 个输入 + 应用 ──
            addLabel(box, "自定义属性（留空=不改）：");
            LinearLayout r2 = new LinearLayout(act);
            r2.setOrientation(LinearLayout.HORIZONTAL);
            final EditText eAtk = small(act, r2, "攻");
            final EditText eDef = small(act, r2, "防");
            final EditText eLife = small(act, r2, "血");
            final EditText eCrit = small(act, r2, "暴击");
            Button ap = new Button(act);
            ap.setText("应用");
            ap.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            ap.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    toast(act, "已应用：" + Srv.setStats(v(eAtk), v(eDef), v(eLife), v(eCrit)));
                }
            });
            r2.addView(ap);
            box.addView(r2);

            // ── 剧情 ──
            LinearLayout r3 = new LinearLayout(act);
            r3.setOrientation(LinearLayout.HORIZONTAL);
            Button rp = new Button(act);
            rp.setText("重放剧情");
            rp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            rp.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            rp.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { toast(act, "已清零 " + Srv.resetStory() + " 关"); }
            });
            r3.addView(rp);
            Button rs = new Button(act);
            rs.setText("恢复全通关");
            rs.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
            rs.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            rs.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { toast(act, "已恢复 " + Srv.restoreStory() + " 关"); }
            });
            r3.addView(rs);
            box.addView(r3);

            android.util.DisplayMetrics dm = act.getResources().getDisplayMetrics();
            int flags = android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
            android.view.WindowManager.LayoutParams lp =
                    new android.view.WindowManager.LayoutParams(
                            (int) (dm.widthPixels * 0.96),
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            android.os.Build.VERSION.SDK_INT >= 26
                                    ? android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                    : android.view.WindowManager.LayoutParams.TYPE_PHONE,
                            flags, android.graphics.PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.LEFT | Gravity.TOP;
            lp.x = 8;
            lp.y = (int) (dm.heightPixels * 0.05);
            if (wm == null) wm = (android.view.WindowManager) act.getSystemService(Activity.WINDOW_SERVICE);
            wm.addView(box, lp);
            sPanel = box;
            Log.i(TAG, "GM 菜单已打开（紧凑布局）");
        } catch (Throwable e) {
            Log.e(TAG, "panel fail", e);
            toast(act, "打开失败：" + e);
        }
    }

    /** 窄输入框 */
    private static EditText small(Activity act, LinearLayout parent, String hint) {
        EditText e = new EditText(act);
        e.setHint(hint);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        e.setTextColor(Color.WHITE);
        e.setHintTextColor(0xFF999999);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        e.setLayoutParams(new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        parent.addView(e);
        return e;
    }

    private static void addLabel(LinearLayout box, String t) {
        TextView v = new TextView(box.getContext());
        v.setText(t);
        v.setTextColor(0xFFBBBBBB);
        box.addView(v);
    }

    private static double v(EditText e) {
        try { String s = e.getText().toString().trim(); return s.isEmpty() ? 0 : Double.parseDouble(s); }
        catch (Exception ex) { return 0; }
    }

    private static void toast(Activity a, String m) {
        Toast.makeText(a, m, Toast.LENGTH_SHORT).show();
    }
}
