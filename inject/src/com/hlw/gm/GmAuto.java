package com.hlw.gm;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
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

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** 一体包入口：启动 Python 服务端 + 在游戏画面上挂一个 GM 按钮。 */
public final class GmAuto {

    private static final String TAG = "HLWGM";
    private static Activity sAct;
    private static TextView sBtn;

    private GmAuto() {}

    public static void start(final Activity act) {
        try {
            sAct = act;
            Log.i(TAG, "start: enter");
            // Chaquopy 必须在主线程初始化，否则原生层会在 mutex 上崩（signal 6）
            act.runOnUiThread(new Runnable() {
                public void run() {
                    try {
                        Log.i(TAG, "start: ui thread, init python");
                        if (!Python.isStarted()) {
                            Python.start(new AndroidPlatform(act.getApplicationContext()));
                        }
                        Log.i(TAG, "start: python started");
                        bootServer(act);
                        addGmButton(act);
                    } catch (Throwable e) {
                        Log.e(TAG, "ui init fail", e);
                    }
                }
            });
        } catch (Throwable e) {
            Log.e(TAG, "start fail", e);
        }
    }

    private static void bootServer(Context ctx) {
        File dir = ctx.getFilesDir();
        if (!dir.exists()) dir.mkdirs();
        PyObject mod = Python.getInstance().getModule("server");
        mod.callAttr("start_all", dir.getAbsolutePath());
        Log.i(TAG, "python server started, dir=" + dir.getAbsolutePath());
    }

    private static void copyAsset(Context ctx, String name, File dst) {
        try {
            if (dst.exists() && dst.length() > 0) return;
            InputStream in = ctx.getAssets().open(name);
            FileOutputStream out = new FileOutputStream(dst);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            out.close(); in.close();
        } catch (Throwable e) {
            Log.e(TAG, "copyAsset " + name, e);
        }
    }

    private static void addGmButton(final Activity act) {
        try {
            final TextView b = new TextView(act);
            b.setText("GM");
            b.setTextColor(Color.WHITE);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            b.setGravity(Gravity.CENTER);
            b.setBackgroundColor(0xCC1E88E5);
            b.setPadding(18, 10, 18, 10);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { showPanel(act); }
            });
            android.widget.FrameLayout.LayoutParams lp =
                    new android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.gravity = Gravity.LEFT | Gravity.TOP;
            lp.leftMargin = 10;
            lp.topMargin = 220;
            act.addContentView(b, lp);
            sBtn = b;
        } catch (Throwable e) {
            Log.e(TAG, "addGmButton fail", e);
        }
    }

    private static void showPanel(final Activity act) {
        try {
            ScrollView sv = new ScrollView(act);
            LinearLayout box = new LinearLayout(act);
            box.setOrientation(LinearLayout.VERTICAL);
            int pad = (int) (10 * act.getResources().getDisplayMetrics().density);
            box.setPadding(pad, pad, pad, pad);
            box.setBackgroundColor(0xF0101010);

            TextView ti = new TextView(act);
            ti.setText("GM 控制台");
            ti.setTextColor(Color.WHITE);
            ti.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
            box.addView(ti);

            final String[] names = {"大娃", "二娃", "三娃", "四娃", "五娃", "六娃", "七娃"};
            TextView l1 = new TextView(act);
            l1.setText("\n切换出战角色（改完请重开游戏）：");
            l1.setTextColor(0xFFBBBBBB);
            box.addView(l1);
            LinearLayout row = null;
            for (int i = 0; i < names.length; i++) {
                if (i % 4 == 0) { row = new LinearLayout(act); box.addView(row); }
                final int idx = i;
                Button b = new Button(act);
                b.setText(names[i]);
                b.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { call(act, "set_role", names[idx], idx); }
                });
                row.addView(b);
            }

            TextView l2 = new TextView(act);
            l2.setText("\n自定义属性（全部角色通用，0=不改）：");
            l2.setTextColor(0xFFBBBBBB);
            box.addView(l2);
            final EditText eAtk = num(act, box, "攻击");
            final EditText eDef = num(act, box, "防御");
            final EditText eLife = num(act, box, "生命");
            final EditText eCrit = num(act, box, "暴击率(0~1)");
            Button apply = new Button(act);
            apply.setText("应用属性");
            apply.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    Object r = call2(act, "set_stats", v(eAtk), v(eDef), v(eLife), v(eCrit), 0.0);
                    Toast.makeText(act, "属性已应用：" + r, Toast.LENGTH_SHORT).show();
                }
            });
            box.addView(apply);

            TextView l3 = new TextView(act);
            l3.setText("\n主线剧情：");
            l3.setTextColor(0xFFBBBBBB);
            box.addView(l3);
            LinearLayout row3 = new LinearLayout(act);
            Button replay = new Button(act);
            replay.setText("重放剧情");
            replay.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { call(act, "reset_story"); }
            });
            row3.addView(replay);
            Button restore = new Button(act);
            restore.setText("恢复全通关");
            restore.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { call(act, "restore_story"); }
            });
            row3.addView(restore);
            box.addView(row3);

            Button close = new Button(act);
            close.setText("关闭");
            box.addView(close);

            sv.addView(box);
            final AlertDialog dlg = new AlertDialog.Builder(act)
                    .setView(sv).create();
            close.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { dlg.dismiss(); }
            });
            dlg.show();
        } catch (Throwable e) {
            Log.e(TAG, "showPanel fail", e);
        }
    }

    private static EditText num(Activity a, LinearLayout box, String hint) {
        EditText e = new EditText(a);
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

    private static Object call(Activity a, String fn, Object... args) {
        try {
            PyObject r = Python.getInstance().getModule("server").callAttr(fn, args);
            String s = r == null ? "" : r.toString();
            Toast.makeText(a, fn + " → " + s, Toast.LENGTH_SHORT).show();
            return s;
        } catch (Throwable e) {
            Toast.makeText(a, fn + " 失败：" + e, Toast.LENGTH_LONG).show();
            return null;
        }
    }

    private static Object call2(Activity a, String fn, Object... args) { return call(a, fn, args); }
}
