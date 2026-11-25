package com.hlw.srv;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/** 服务端入口：初始化存档并启动 HTTP/TCP。 */
public final class Srv {

    private static boolean started = false;

    private Srv() {}

    public static synchronized void start(android.content.Context ctx) {
        if (started) return;
        started = true;
        File dataDir = ctx.getFilesDir();
        if (!dataDir.exists()) dataDir.mkdirs();
        Store.init(dataDir, ctx.getAssets());
        Http.start();
        Tcp.start();
        Log_.i("服务端已启动，数据目录 " + dataDir.getAbsolutePath());
    }

    // ───────── GM 用的便捷接口 ─────────
    public static String setRole(int idx) {
        if (idx < 0 || idx > 6) return "bad";
        Store.setSltRole("role" + idx);
        Map<String, Object> teams = Store.objIn(Store.group("1"), "teams");
        Map<String, Object> t0 = Store.objIn(teams, "team0");
        java.util.List<Object> one = new java.util.ArrayList<Object>();
        one.add("role" + idx);
        t0.put("team", one);
        t0.put("id", 0);
        t0.put("teamName", "战队1");
        Store.save();
        return Store.ROLE_NAMES[idx];
    }

    public static String setStats(double atk, double def, double life, double crit) {
        Map<String, Object> c = Store.objIn(Store.ud(), "custom");
        if (atk > 0) c.put("atk", Integer.valueOf((int) atk));
        if (def > 0) c.put("def", Integer.valueOf((int) def));
        if (life > 0) c.put("life", Integer.valueOf((int) life));
        if (crit > 0) c.put("crit", Double.valueOf(crit));
        Store.save();
        return Json.write(c);
    }

    public static String resetStory() {
        Map<String, Object> st = Store.story();
        int n = 0;
        for (Map.Entry<String, Object> e : st.entrySet()) {
            Map<String, Object> rec = Json.obj(e.getValue());
            rec.put("bastJudge", 0);
            n++;
        }
        Map<String, Object> war = Store.objIn(Store.group("7"), "war");
        war.put("now_g", 0);
        war.put("now_i", 0);
        Store.save();
        return String.valueOf(n);
    }

    public static String restoreStory() {
        Map<String, Object> st = Store.story();
        int n = 0;
        for (Map.Entry<String, Object> e : st.entrySet()) {
            Map<String, Object> rec = Json.obj(e.getValue());
            rec.put("bastJudge", 3);
            n++;
        }
        Map<String, Object> war = Store.objIn(Store.group("7"), "war");
        war.put("now_g", 14);
        war.put("now_i", 6);
        Store.save();
        return String.valueOf(n);
    }

    public static String status() {
        return "服务端运行中 · 当前角色 " + Store.sltRole();
    }
}
