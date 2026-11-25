package com.hlw.srv;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 玩家数据存档（对应 Python 版的 player_ud.json）。 */
public final class Store {

    public static final String[] ROLE_NAMES = {"大娃", "二娃", "三娃", "四娃", "五娃", "六娃", "七娃"};
    /** 每章关数（进关卡算 next 用） */
    public static final int[] CHAPTER_LEVELS = {8, 6, 7, 6, 5, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7};

    private static final Map<String, Object> UD = new LinkedHashMap<String, Object>();
    private static File dir;
    private static File file;
    private static String sltRole = "role0";

    private Store() {}

    public static synchronized void init(File dataDir, Object assetManager) {
        dir = dataDir;
        if (!dir.exists()) dir.mkdirs();
        file = new File(dir, "player_ud.json");
        if (file.exists()) {
            try {
                byte[] b = readAll(file);
                Object o = Json.parse(new String(b, "UTF-8"));
                if (o instanceof Map) {
                    UD.clear();
                    UD.putAll(Json.obj(o));
                    Log_.i("载入存档 " + file.length() + " 字节, 组数 " + UD.size());
                    return;
                }
            } catch (Throwable e) { Log_.e("读存档失败", e); }
        }
        // 首次启动：从 APK 内置 assets 复制初始存档（满级存档，别人装上即可玩）
        try {
            java.lang.reflect.Method open = assetManager.getClass()
                    .getMethod("open", String.class);
            java.io.InputStream in = (java.io.InputStream) open.invoke(assetManager, "hlw/player_ud.json");
            FileOutputStream fo = new FileOutputStream(file);
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
            fo.close(); in.close();
            Object o = Json.parse(new String(readAll(file), "UTF-8"));
            if (o instanceof Map) {
                UD.clear();
                UD.putAll(Json.obj(o));
                Log_.i("已从内置资产复制存档，组数 " + UD.size());
                return;
            }
        } catch (Throwable e) {
            Log_.e("复制内置存档失败（将使用空数据）", e);
        }
        Log_.i("没有存档，使用空数据");
    }

    public static Map<String, Object> ud() { return UD; }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> group(String g) {
        Object o = UD.get(g);
        if (!(o instanceof Map)) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            UD.put(g, m);
            return m;
        }
        return (Map<String, Object>) o;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> objIn(Map<String, Object> parent, String key) {
        Object o = parent.get(key);
        if (!(o instanceof Map)) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            parent.put(key, m);
            return m;
        }
        return (Map<String, Object>) o;
    }

    public static String sltRole() { return sltRole; }

    public static void setSltRole(String r) {
        sltRole = r;
        objIn(group("1"), "sltRolemark"); // 占位，保持结构
        group("1").put("sltRole", r);
        save();
    }

    public static synchronized void save() {
        try {
            FileOutputStream fo = new FileOutputStream(file);
            fo.write(Json.write(UD).getBytes("UTF-8"));
            fo.close();
        } catch (Throwable e) { Log_.e("写存档失败", e); }
    }

    /** 当前出战角色的数字 id */
    public static int sltIndex() {
        try { return Integer.parseInt(sltRole.replace("role", "")); } catch (Exception e) { return 0; }
    }

    /** 关卡通关记录键：g{章}_i{关} */
    public static Map<String, Object> story() { return objIn(group("6"), "story"); }

    public static void recordWin(int g, int i) {
        Map<String, Object> st = story();
        String key = "g" + g + "_i" + i;
        Map<String, Object> rec = objIn(st, key);
        rec.put("bastJudge", Math.max(Json.i(rec.get("bastJudge"), 0), 1));
        Map<String, Object> roles = objIn(rec, "roles");
        Map<String, Object> all = objIn(roles, "all");
        all.put("winTimes", Json.i(all.get("winTimes"), 0) + 1);
        if (all.get("failTimes") == null) all.put("failTimes", 0);
        // 进度指针推进
        Map<String, Object> war = objIn(group("7"), "war");
        war.put("now_g", g);
        war.put("now_i", i);
        save();
        Log_.i("记录通关 " + key + " bastJudge=" + rec.get("bastJudge"));
    }

    /** 战斗用的角色快照（userDGroup[7].war.r0） */
    public static Map<String, Object> buildMaster() {
        int idx = sltIndex();
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", ROLE_NAMES[idx]);
        m.put("url", "fighterg1401" + (idx + 1));
        m.put("id", "HL10001");
        m.put("roleid", idx);
        m.put("level", 90);
        m.put("vip", 15);
        m.put("showVIP", 1);
        m.put("headPic", "0");
        m.put("comboType", 0);
        // 属性：可在 GM 面板里覆盖（custom 节点）
        Map<String, Object> c = Json.obj(UD.get("custom"));
        m.put("atk", Json.i(c.get("atk"), 4746));
        m.put("def", Json.i(c.get("def"), 3818));
        m.put("life", Json.i(c.get("life"), 68257));
        m.put("speed", 10);
        m.put("crit", c.containsKey("crit") ? c.get("crit") : Double.valueOf(0.09));
        m.put("critFactor", c.containsKey("critFactor") ? c.get("critFactor") : Double.valueOf(1.38));
        m.put("sDmgRdc", 0);
        m.put("atk_factor", 1);
        m.put("def_factor", 1);
        m.put("speed_factor", 1);
        m.put("crit_factor", 1);
        m.put("life_factor", 1);
        m.put("critFactor_factor", 1);
        m.put("sDmgRdc_factor", 1);
        // 武器外观：留空，避免把别的角色模型套上来（客户端按 roleid+武器类型拼名字）
        m.put("extrsEquip", new ArrayList<Object>());
        List<Object> sk = new ArrayList<Object>();
        for (int k : new int[]{0, 100, 200}) {
            Map<String, Object> s = new LinkedHashMap<String, Object>();
            int id = (k == 0) ? idx : (k + idx);
            s.put("id", "skill" + id);
            s.put("lv", 98);
            s.put("state", 1);
            sk.add(s);
        }
        m.put("skill", sk);
        m.put("grade", 6);
        m.put("deify", 0);
        m.put("groupID", "");
        m.put("groupName", "帮会名字");
        return m;
    }

    // ───────── 文件工具 ─────────
    public static byte[] readAll(File f) throws Exception {
        FileInputStream in = new FileInputStream(f);
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        in.close();
        return bo.toByteArray();
    }
}
