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
    /** 客户端 CSInitPlayer 上报的完整自身数据（战斗就靠它） */
    private static Map<String, Object> LAST_PLAYER = new LinkedHashMap<String, Object>();

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

    /** 收到 CSInitPlayer：存下客户端自算的完整战斗数据 */
    public static synchronized void setLastPlayer(String json) {
        try {
            Object o = Json.parse(json);
            if (o instanceof Map) {
                LAST_PLAYER.clear();
                LAST_PLAYER.putAll(Json.obj(o));
                Log_.i("已保存客户端战斗快照，字段数 " + LAST_PLAYER.size());
            }
        } catch (Throwable e) { Log_.e("解析 CSInitPlayer 失败", e); }
    }

    public static boolean hasLastPlayer() { return !LAST_PLAYER.isEmpty(); }

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

    /** 战斗用的角色快照（userDGroup[7].war.r0）——完全对齐 Python 版 get_master() */
    public static Map<String, Object> buildMaster() {
        int idx = sltIndex();
        Map<String, Object> src = LAST_PLAYER.isEmpty() ? new LinkedHashMap<String, Object>() : LAST_PLAYER;
        Map<String, Object> c = Json.obj(UD.get("custom"));

        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("name", ROLE_NAMES[idx]);
        m.put("url", "fighterg1401" + (idx + 1));
        m.put("id", Json.s(src.get("id"), "HL10001"));
        m.put("roleid", idx);
        m.put("level", Json.i(src.get("level"), 90));
        m.put("vip", 15);
        m.put("showVIP", 1);
        m.put("headPic", String.valueOf(idx));
        m.put("comboType", Json.i(src.get("comboType"), 0));

        // 属性（战斗读这套名字）
        m.put("atk", c.containsKey("atk") ? c.get("atk") : Integer.valueOf(Json.i(src.get("attack"), 4746)));
        m.put("def", c.containsKey("def") ? c.get("def") : Integer.valueOf(Json.i(src.get("defence"), 3818)));
        m.put("life", c.containsKey("life") ? c.get("life")
                : Integer.valueOf(Json.i(src.get("realLife"), Json.i(src.get("life"), 68257))));
        m.put("speed", Integer.valueOf(Json.i(src.get("maxSpeed"), 10)));
        m.put("crit", c.containsKey("crit") ? c.get("crit") : Double.valueOf(0.09));
        m.put("critFactor", c.containsKey("critFactor") ? c.get("critFactor") : Double.valueOf(1.38));
        m.put("sDmgRdc", Integer.valueOf(0));

        // 系数
        m.put("atk_factor", Integer.valueOf(1));
        m.put("def_factor", Integer.valueOf(1));
        m.put("speed_factor", Integer.valueOf(1));
        m.put("crit_factor", Integer.valueOf(1));
        m.put("life_factor", Integer.valueOf(1));
        m.put("critFactor_factor", Integer.valueOf(1));
        m.put("sDmgRdc_factor", Integer.valueOf(1));

        // 装备外观：只在该快照确实属于当前角色时才带
        String roleIdOfSrc = Json.s(src.get("roleID"), Json.s(src.get("roleid"), ""));
        m.put("extrsEquip", roleIdOfSrc.equals(String.valueOf(idx))
                ? filterProps(Json.arr(src.get("props")), idx)
                : new ArrayList<Object>());

        // 技能（每个角色自己的 id）
        List<Object> sk = new ArrayList<Object>();
        for (String id : new String[]{"skill" + idx, "skill10" + idx, "skill20" + idx}) {
            Map<String, Object> one = new LinkedHashMap<String, Object>();
            one.put("id", id);
            one.put("lv", 98);
            one.put("state", 1);
            sk.add(one);
        }
        m.put("skill", sk);

        m.put("grade", Json.i(src.get("grade"), 6));
        m.put("deify", Json.i(src.get("deify"), 0));
        m.put("groupID", "");
        m.put("groupName", Json.s(src.get("groupName"), "帮会名字"));

        if (c.containsKey("life")) {
            m.put("realLife", c.get("life"));
            m.put("maxLife", c.get("life"));
        }
        return m;
    }

    /** 只保留属于该角色的装备外观（display.url 含 fighterg1401{N+1}） */
    private static List<Object> filterProps(List<Object> props, int idx) {
        List<Object> out = new ArrayList<Object>();
        if (props == null) return out;
        String tag = "fighterg1401" + (idx + 1);
        for (Object o : props) {
            Map<String, Object> p = Json.obj(o);
            boolean keep = false;
            for (Object d : Json.arr(p.get("display"))) {
                String u = Json.s(Json.obj(d).get("url"), "");
                if (u.contains(tag)) { keep = true; break; }
            }
            if (keep) out.add(p);
        }
        return out;
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
