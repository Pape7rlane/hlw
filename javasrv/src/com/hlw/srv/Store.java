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

    /** 战斗用的角色快照（userDGroup[7].war.r0） */
    public static Map<String, Object> buildMaster() {
        int idx = sltIndex();
        if (!LAST_PLAYER.isEmpty()) return masterFromClient(idx);
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
        // 属性：全部使用客户端字段名（与 Python 版一致，缺字段会导致 startEnter 抛错）
        Map<String, Object> c = Json.obj(UD.get("custom"));
        m.put("attack", c.containsKey("atk") ? c.get("atk") : Integer.valueOf(4746));
        m.put("defence", c.containsKey("def") ? c.get("def") : Integer.valueOf(3818));
        m.put("life", c.containsKey("life") ? c.get("life") : Integer.valueOf(147717));
        m.put("realLife", c.containsKey("life") ? c.get("life") : Integer.valueOf(147717));
        m.put("maxLife", c.containsKey("life") ? c.get("life") : Integer.valueOf(147717));
        m.put("realMaxLife", c.containsKey("life") ? c.get("life") : Integer.valueOf(147717));
        m.put("maxSpeed", Integer.valueOf(10));
        m.put("crit", c.containsKey("crit") ? c.get("crit") : Double.valueOf(0.09));
        m.put("critHit", Double.valueOf(1.38));
        m.put("critFactor", Double.valueOf(1));
        m.put("critHitFactor", Double.valueOf(1));
        m.put("attackFactor", Integer.valueOf(1));
        m.put("defenceFactor", Integer.valueOf(1));
        m.put("maxLifeFactor", Integer.valueOf(1));
        m.put("maxSpeedFactor", Integer.valueOf(1));
        m.put("sDmgRdc", Integer.valueOf(0));
        m.put("sDmgRdcFactor", Integer.valueOf(1));
        m.put("fightPower", Integer.valueOf(1200000));
        m.put("frameRate", Integer.valueOf(1));
        m.put("killPlayerNum", Integer.valueOf(0));
        m.put("showPlayerNum", Integer.valueOf(8));
        m.put("roleExtend", "{}");
        m.put("groupName", "帮会名字");
        m.put("props", new ArrayList<Object>());
        m.put("blacklist", new ArrayList<Object>());
        m.put("enemy", new ArrayList<Object>());
        m.put("friends", new ArrayList<Object>());
        m.put("loser", new ArrayList<Object>());
        m.put("special", new ArrayList<Object>());
        m.put("skill", sk);
        m.put("grade", 6);
        m.put("deify", 0);
        m.put("groupID", "");
        m.put("groupName", "帮会名字");
        return m;
    }

    /** 用客户端上报的数据拼 7.war.r0：字段齐全，只覆盖身份与武器外观 */
    private static Map<String, Object> masterFromClient(int idx) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.putAll(LAST_PLAYER);
        m.put("url", "fighterg1401" + (idx + 1));
        m.put("name", ROLE_NAMES[idx]);
        m.put("roleid", String.valueOf(idx));
        m.put("level", 90);
        m.put("vip", 15);
        m.put("showVIP", 1);
        // 技能换成该角色的
        List<Object> sk = new ArrayList<Object>();
        for (int k : new int[]{0, 100, 200}) {
            Map<String, Object> one = new LinkedHashMap<String, Object>();
            one.put("id", "skill" + (k == 0 ? idx : k + idx));
            one.put("lv", 98);
            one.put("state", 1);
            sk.add(one);
        }
        m.put("skill", sk);
        // 武器外观只保留属于本角色的，避免串模型
        m.put("props", filterProps(Json.arr(LAST_PLAYER.get("props")), idx));
        // ★ r0 必须使用客户端字段名（attack/defence/critHit/fightPower/...），
        //   缺任何一个，客户端 startEnter()→exit() 都会抛异常并被静默吞掉（表现为卡死）。
        ensure(m, "attack",   c0(pick(m,"attack","atk"), 4746));
        ensure(m, "defence",  c0(pick(m,"defence","def"), 3818));
        ensure(m, "life",     c0(m.get("life"), 147717));
        ensure(m, "realLife", c0(m.get("realLife"), 147717));
        ensure(m, "maxLife",  c0(m.get("maxLife"), 147717));
        ensure(m, "maxSpeed", c0(pick(m,"maxSpeed","speed"), 10));
        ensure(m, "crit",     c0(m.get("crit"), 0.09));
        ensure(m, "critHit",  c0(m.get("critHit"), 1.38));
        ensure(m, "critFactor", c0(m.get("critFactor"), 1));
        ensure(m, "critHitFactor", c0(m.get("critHitFactor"), 1));
        ensure(m, "attackFactor", c0(pick(m,"attackFactor","atk_factor"), 1));
        ensure(m, "defenceFactor", c0(pick(m,"defenceFactor","def_factor"), 1));
        ensure(m, "maxLifeFactor", c0(pick(m,"maxLifeFactor","life_factor"), 1));
        ensure(m, "maxSpeedFactor", c0(pick(m,"maxSpeedFactor","speed_factor"), 1));
        ensure(m, "fightPower", c0(m.get("fightPower"), 1200000));
        ensure(m, "frameRate", c0(m.get("frameRate"), 1));
        ensure(m, "killPlayerNum", c0(m.get("killPlayerNum"), 0));
        ensure(m, "comboType", c0(m.get("comboType"), 0));
        ensure(m, "showPlayerNum", c0(m.get("showPlayerNum"), 8));
        ensure(m, "hp", null);
        m.remove("hp");
        if (!(m.get("blacklist") instanceof List)) m.put("blacklist", new ArrayList<Object>());
        if (!(m.get("enemy") instanceof List)) m.put("enemy", new ArrayList<Object>());
        if (!(m.get("friends") instanceof List)) m.put("friends", new ArrayList<Object>());
        if (!(m.get("loser") instanceof List)) m.put("loser", new ArrayList<Object>());
        if (!(m.get("special") instanceof List)) m.put("special", new ArrayList<Object>());
        if (!(m.get("roleExtend") instanceof String)) m.put("roleExtend", "{}");
        if (!(m.get("groupName") instanceof String)) m.put("groupName", "帮会名字");

        // 自定义属性覆盖
        Map<String, Object> c = Json.obj(group("custom"));
        if (c.containsKey("atk")) m.put("attack", c.get("atk"));
        if (c.containsKey("def")) m.put("defence", c.get("def"));
        if (c.containsKey("life")) { m.put("life", c.get("life")); m.put("realLife", c.get("life")); m.put("maxLife", c.get("life")); }
        if (c.containsKey("crit")) m.put("crit", c.get("crit"));
        return m;
    }

    private static Object c0(Object v, Object d) { return (v == null) ? d : v; }

    /** 依次取第一个非 null 的键 */
    private static Object pick(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) return v;
        }
        return null;
    }

    /** 字段不存在或为 null 时补默认值 */
    private static void ensure(Map<String, Object> m, String k, Object d) {
        if (!m.containsKey(k) || m.get(k) == null) m.put(k, d);
    }

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
