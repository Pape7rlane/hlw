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

    private static void addButton(final Activity act) {
        // AIR 的画面层会盖住 addContentView 加进去的 View，必须挂到 decorView 上，
        // 并且等 AIR 自己把 SurfaceView 加完（约 2~4 秒）再贴上去。
        act.getWindow().getDecorView().postDelayed(new Runnable() {
            public void run() {
                try {
                    TextView b = new TextView(act);
                    b.setText("GM");
                    b.setTextColor(Color.WHITE);
                    b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                    b.setGravity(Gravity.CENTER);
                    b.setBackgroundColor(0xDD1E88E5);
                    b.setPadding(26, 12, 26, 12);
                    b.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) { panel(act); }
                    });
                    android.widget.FrameLayout.LayoutParams lp =
                            new android.widget.FrameLayout.LayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT);
                    lp.gravity = Gravity.LEFT | Gravity.TOP;
                    lp.leftMargin = 16;
                    lp.topMargin = 260;
                    android.view.ViewGroup root =
                            (android.view.ViewGroup) act.getWindow().getDecorView();
                    root.addView(b, lp);
                    b.bringToFront();
                    Log.i(TAG, "GM 按钮已挂到 decorView");
                } catch (Throwable e) {
                    Log.e(TAG, "addButton fail", e);
                }
            }
        }, 3500);
    }

    private static void panel(final Activity act) {
        ScrollView sv = new ScrollView(act);
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

        addLabel(box, "\n切换出战角色（改完请重开游戏）：");
        String[] names = {"大娃", "二娃", "三娃", "四娃", "五娃", "六娃", "七娃"};
        LinearLayout row = null;
        for (int i = 0; i < names.length; i++) {
            if (i % 4 == 0) { row = new LinearLayout(act); box.addView(row); }
            final int idx = i;
            Button b = new Button(act);
            b.setText(names[i]);
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    toast(act, "已切换：" + Srv.setRole(idx) + "（请重开游戏）");
                }
            });
            row.addView(b);
        }

        addLabel(box, "\n自定义属性（0 或留空=不改）：");
        final EditText a = num(act, box, "攻击");
        final EditText d = num(act, box, "防御");
        final EditText l = num(act, box, "生命");
        final EditText c = num(act, box, "暴击率 0~1");
        Button ap = new Button(act);
        ap.setText("应用属性");
        ap.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                toast(act, "已应用：" + Srv.setStats(v(a), v(d), v(l), v(c)));
            }
        });
        box.addView(ap);

        addLabel(box, "\n主线剧情：");
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
        box.addView(close);

        sv.addView(box);
        final AlertDialog dlg = new AlertDialog.Builder(act).setView(sv).create();
        close.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { dlg.dismiss(); }
        });
        dlg.show();
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
