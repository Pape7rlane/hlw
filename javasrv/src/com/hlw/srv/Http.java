package com.hlw.srv;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;

/** HTTP 服务端（8080）：服务器列表 / 会话 / 配置 / 进关卡 / 结算。 */
public final class Http {

    public static final int PORT = 8080;

    /** info= 参数的自定义 base64 字母表 */
    private static final String B64 =
            "C*y0p]oPc[YrRi9htGuU4FaAnNg6BDEH7IOJKL5MQSTVWXZbdefjkl3mqs1vwxz28";

    private Http() {}

    public static void start() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                ServerSocket ss = null;
                try {
                    ss = new ServerSocket(PORT);
                    Log_.i("HTTP 监听 " + PORT);
                    while (true) {
                        final Socket s = ss.accept();
                        Thread th = new Thread(new Runnable() {
                            public void run() { handle(s); }
                        });
                        th.setDaemon(true);
                        th.start();
                    }
                } catch (Throwable e) {
                    Log_.e("HTTP 启动失败", e);
                }
            }
        }, "hlw-http");
        t.setDaemon(true);
        t.start();
    }

    // ───────── 单个连接 ─────────
    private static void handle(Socket s) {
        try {
            InputStream in = s.getInputStream();
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            int c;
            // 读到空行（\r\n\r\n）为止；之前逐行计数判断写错了，会一直读下去导致请求无应答
            while ((c = in.read()) >= 0) {
                head.write(c);
                byte[] hb = head.toByteArray();
                int n = hb.length;
                if (n >= 4 && hb[n-4] == '\r' && hb[n-3] == '\n' && hb[n-2] == '\r' && hb[n-1] == '\n') break;
                if (n >= 2 && hb[n-2] == '\n' && hb[n-1] == '\n') break;
                if (n > 65536) break;
            }
            String h = new String(head.toByteArray(), "UTF-8");
            String[] lines = h.split("\r?\n");
            String path = "/";
            if (lines.length > 0) {
                String[] p = lines[0].split(" ");
                if (p.length > 1) path = p[1];
            }
            int len = 0;
            for (String l : lines) {
                if (l.toLowerCase().startsWith("content-length:")) {
                    len = Integer.parseInt(l.substring(15).trim());
                }
            }
            byte[] body = new byte[len];
            int got = 0;
            while (got < len) {
                int n = in.read(body, got, len - got);
                if (n <= 0) break;
                got += n;
            }
            route(s, path, new String(body, "UTF-8"));
        } catch (Throwable e) {
            Log_.e("HTTP 连接处理异常", e);   // 之前这里静默吞掉，出问题完全看不到
        } finally {
            try { s.close(); } catch (Throwable ignored) {}
        }
    }

    private static void route(Socket s, String rawPath, String body) throws Exception {
        String path = rawPath.split("\\?")[0];
        String lower = path.toLowerCase();

        if (lower.contains("socketcfg") || lower.endsWith(".xml")) {
            send(s, "<cfg><domain>127.0.0.1</domain><port>9000</port></cfg>".getBytes("UTF-8"),
                    "text/xml; charset=utf-8", false);
            return;
        }
        if (lower.contains("getserver") || lower.endsWith(".htm")) {
            Map<String, Object> o = new LinkedHashMap<String, Object>();
            o.put("servernum", 1);
            o.put("server0", "http://127.0.0.1:8080/");
            o.put("server0back0", "http://127.0.0.1:8080/");
            o.put("server0back1", "http://127.0.0.1:8080/");
            o.put("servercode0", "1");
            o.put("cutinto0", 0);
            o.put("st0", 1);
            o.put("getsession0", "http://127.0.0.1:8080/getsession.php");
            List<Object> ids = new ArrayList<Object>();
            ids.add(1);
            o.put("serverid0", ids);
            o.put("msgbox0", "");
            o.put("servername0", "LocalServer");
            o.put("socketURL0", "http://127.0.0.1:8080/socketcfg.xml");
            send(s, Json.write(o).getBytes("UTF-8"), "text/plain; charset=utf-8", false);
            return;
        }
        if (lower.contains("getsession") || lower.contains("check_login")) {
            Map<String, Object> o = new LinkedHashMap<String, Object>();
            List<Object> uid = new ArrayList<Object>(); uid.add("10001");
            List<Object> ses = new ArrayList<Object>(); ses.add("LOCALSESSION0001");
            List<Object> nu = new ArrayList<Object>(); nu.add("0");
            List<Object> ol = new ArrayList<Object>(); ol.add(Boolean.FALSE);
            o.put("uid", uid);
            o.put("session", ses);
            o.put("login", "ok");
            o.put("newuser", nu);
            o.put("olduid", ol);
            send(s, zlib(Json.write(o)), "application/octet-stream", false);
            return;
        }
        if (lower.contains("elog") || lower.contains("/l.php")) {
            Log_.i("[CLIENT] " + (body.length() > 400 ? body.substring(0, 400) : body));
            send(s, new byte[0], "text/plain", false);
            return;
        }
        if (lower.contains("update_reslist")) {
            Map<String, Object> o = new LinkedHashMap<String, Object>();
            o.put("msg", "ok");
            o.put("ver", 1);
            List<Object> srv = new ArrayList<Object>();
            srv.add("http://127.0.0.1:8080");
            o.put("servers", srv);
            o.put("allRes", new LinkedHashMap<String, Object>());
            o.put("moduleList", new ArrayList<Object>());
            o.put("dirList", new ArrayList<Object>());
            send(s, zlib(Json.write(o)), "application/octet-stream", false);
            return;
        }
        if (lower.endsWith(".pack")) {
            send(s, new byte[0], "text/plain", false);
            return;
        }
        if (lower.contains("platinfo")) {
            send(s, "{\"code\":0,\"msg\":\"ok\"}".getBytes("UTF-8"), "text/plain", false);
            return;
        }
        if (lower.contains("cb.php")) {
            Map<String, Object> q = parseForm(body);
            int type = toInt(first(q, "type"), 0);
            String infoRaw = first(q, "info");
            Map<String, Object> info = decodeInfo(infoRaw);
            Log_.i("cb " + type + " info=" + Json.write(info));
            Map<String, Object> payload = cb(type, info);
            byte[] outBytes = zlib(Json.write(payload));
            Log_.i("cb " + type + " resp=" + Json.write(payload) + " (" + outBytes.length + "B zlib)");
            send(s, outBytes, "application/octet-stream", false);
            return;
        }
        send(s, new byte[0], "text/plain", false);
    }

    // ───────── cb.php 各 type ─────────
    private static Map<String, Object> cb(int t, Map<String, Object> info) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        out.put("msg", "ok");
        out.put("act", null);

        switch (t) {
            case 0: {
                Map<String, Object> ud = Store.ud();
                Map<String, Object> g7 = Store.group("7");
                Map<String, Object> war = Store.objIn(g7, "war");
                war.put("r0", Store.buildMaster());
                Map<String, Object> g0 = Store.group("0");
                g0.put("vipLevel", 15);
                Map<String, Object> g2 = Store.group("2");
                Map<String, Object> act = Store.objIn(g2, "activity");
                if (!act.containsKey("vipReward")) act.put("vipReward", new LinkedHashMap<String, Object>());
                out.put("returndata", ud);
                break;
            }
            case 2: {
                Map<String, Object> rd = new LinkedHashMap<String, Object>();
                rd.put("deleteCfgs", new ArrayList<Object>());
                Map<String, Object> upd = new LinkedHashMap<String, Object>();
                Map<String, Object> roleCon = new LinkedHashMap<String, Object>();
                List<Object> ids = new ArrayList<Object>();
                for (int i = 0; i < 7; i++) ids.add(i);
                roleCon.put("totalIdArr", ids);
                upd.put("roleCon", roleCon);
                rd.put("updateCfgs", upd);
                Map<String, Object> ov = new LinkedHashMap<String, Object>();
                Map<String, Object> ext = new LinkedHashMap<String, Object>();
                ext.put("ver", 1);
                ext.put("debugAs", null);
                ext.put("dtSystemArr", new ArrayList<Object>());
                ov.put("extatvBtnList", ext);
                rd.put("overrideCfgs", ov);
                out.put("returndata", rd);
                break;
            }
            case 44444: {
                Map<String, Object> rd = new LinkedHashMap<String, Object>();
                int g = Json.i(info.get("gr_id"), 0);
                int i = Json.i(info.get("in_id"), 0);
                int cnt = (g >= 0 && g < Store.CHAPTER_LEVELS.length) ? Store.CHAPTER_LEVELS[g] : 7;
                Map<String, Object> next = new LinkedHashMap<String, Object>();
                if (i + 1 < cnt) {
                    next.put("gr_id", "group" + g);
                    next.put("in_id", "instance" + (i + 1));
                } else if (g + 1 < Store.CHAPTER_LEVELS.length) {
                    next.put("gr_id", "group" + (g + 1));
                    next.put("in_id", "instance0");
                }
                if (!next.isEmpty()) rd.put("next", next);
                out.put("returndata", rd);
                Log_.i("进关卡 gr=" + g + " in=" + i + " next=" + Json.write(next));
                break;
            }
            case 44445: {
                int win = Json.i(info.get("isWin"), 0);
                Map<String, Object> props = Json.obj(info.get("prop"));
                Map<String, Object> bag = Store.objIn(Store.group("2"), "props");
                Map<String, Object> added = new LinkedHashMap<String, Object>();
                for (Map.Entry<String, Object> e : props.entrySet()) {
                    String pid = e.getKey();
                    int num = Json.i(e.getValue(), 0);
                    Map<String, Object> cur = Store.objIn(bag, pid);
                    cur.put("id", pid);
                    cur.put("num", Json.i(cur.get("num"), 0) + num);
                    Map<String, Object> a = new LinkedHashMap<String, Object>();
                    a.put("id", pid);
                    a.put("num", num);
                    added.put(pid, a);
                }
                int g = Json.i(info.get("gr_id"), -1);
                int i = Json.i(info.get("in_id"), -1);
                if (win == 1 && g >= 0 && i >= 0) Store.recordWin(g, i);
                Store.save();
                out.put("returndataNew", added);
                List<Object> up = new ArrayList<Object>();
                up.add("2|props|" + Json.write(bag));
                out.put("update", up);
                Log_.i("结算 win=" + win + " 掉落=" + Json.write(added));
                break;
            }
            case 5422: {
                String at = Json.s(info.get("atype"), "");
                if ("viptequan".equals(at) || "lingIndex".equals(at)) {
                    Map<String, Object> g2 = Store.group("2");
                    Map<String, Object> act = Store.objIn(g2, "activity");
                    Map<String, Object> vr = Store.objIn(act, "vipReward");
                    Map<String, Object> bag = Store.objIn(g2, "props");
                    Map<String, Object> grant = new LinkedHashMap<String, Object>();
                    String now = String.valueOf(System.currentTimeMillis() / 1000);
                    for (int lv : new int[]{6, 12}) {
                        Map<String, Object> node = Store.objIn(vr, "vip" + lv);
                        node.put("lastling", now);
                        String pid = (lv == 6) ? "prop598" : "prop599";
                        Map<String, Object> cur = Store.objIn(bag, pid);
                        cur.put("id", pid);
                        cur.put("num", Json.i(cur.get("num"), 0) + 1);
                        Map<String, Object> a = new LinkedHashMap<String, Object>();
                        a.put("id", pid); a.put("num", 1);
                        grant.put(pid, a);
                    }
                    Store.save();
                    out.put("returndataNew", grant);
                    List<Object> up = new ArrayList<Object>();
                    up.add("2|activity|" + Json.write(act));
                    out.put("update", up);
                    Log_.i("VIP 福利已领取");
                } else {
                    out.put("returndata", new LinkedHashMap<String, Object>());
                }
                break;
            }
            case 340: {
                // 装备穿脱（up_batch / down_batch）
                String act = Json.s(info.get("actType"), "");
                Map<String, Object> rd = new LinkedHashMap<String, Object>();
                out.put("returndata", rd);
                Log_.i("装备操作 " + act);
                break;
            }
            case 233: {
                List<Object> add = Json.arr(info.get("addTeam"));
                int cid = Json.i(info.get("curTeamId"), 0);
                Map<String, Object> teams = Store.objIn(Store.group("1"), "teams");
                Map<String, Object> team = Store.objIn(teams, "team" + cid);
                if (!add.isEmpty()) {
                    List<Object> picked = new ArrayList<Object>();
                    picked.add(add.get(add.size() - 1));
                    team.put("team", picked);
                    team.put("id", cid);
                    team.put("teamName", "战队1");
                    Store.setSltRole(String.valueOf(picked.get(0)));
                }
                Map<String, Object> rd = new LinkedHashMap<String, Object>();
                Map<String, Object> td = new LinkedHashMap<String, Object>();
                Map<String, Object> one = new LinkedHashMap<String, Object>();
                one.put("team", team.get("team"));
                one.put("curTeamId", cid);
                td.put("team" + cid, one);
                rd.put("teamData", td);
                out.put("returndata", rd);
                break;
            }
            case 1001: {
                out.put("returndata", new LinkedHashMap<String, Object>());
                break;
            }
            default: {
                out.put("returndata", new LinkedHashMap<String, Object>());
                Log_.i("cb type=" + t + " 未实现，回空");
            }
        }
        return out;
    }

    // ───────── info 解码（自定义 base64 + 32 位 md5 前缀）─────────
    @SuppressWarnings("unchecked")
    static Map<String, Object> decodeInfo(String s) {
        if (s == null || s.length() < 34) return new LinkedHashMap<String, Object>();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        int bits = 0, n = 0;
        for (int i = 32; i < s.length(); i++) {
            int v = B64.indexOf(s.charAt(i));
            if (v < 0) continue;
            bits = (bits << 6) | v;
            n += 6;
            if (n >= 8) { n -= 8; bo.write((bits >> n) & 0xFF); }
        }
        String txt = new String(bo.toByteArray()).replace("\u0000", "");
        int a = txt.indexOf('{'), b = txt.lastIndexOf('}');
        if (a < 0 || b <= a) return new LinkedHashMap<String, Object>();
        try {
            Object o = Json.parse(txt.substring(a, b + 1));
            return Json.obj(o);
        } catch (Throwable e) {
            return new LinkedHashMap<String, Object>();
        }
    }

    // ───────── 工具 ─────────
    static Map<String, Object> parseForm(String body) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        if (body == null) return m;
        for (String kv : body.split("&")) {
            int e = kv.indexOf('=');
            if (e <= 0) continue;
            m.put(urlDecode(kv.substring(0, e)), urlDecode(kv.substring(e + 1)));
        }
        return m;
    }

    static String first(Map<String, Object> m, String k) {
        Object o = m.get(k);
        return o == null ? "" : String.valueOf(o);
    }

    static int toInt(String s, int d) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return d; }
    }

    static String urlDecode(String s) {
        try { return java.net.URLDecoder.decode(s, "UTF-8"); } catch (Throwable e) { return s; }
    }

    static byte[] zlib(String text) {
        byte[] data;
        try { data = text.getBytes("UTF-8"); } catch (Throwable e) { data = text.getBytes(); }
        return zlibBytes(data);
    }

    static byte[] zlibBytes(byte[] data) {
        Deflater d = new Deflater();
        d.setInput(data);
        d.finish();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!d.finished()) {
            int n = d.deflate(buf);
            bo.write(buf, 0, n);
        }
        d.end();
        return bo.toByteArray();
    }

    static void send(Socket s, byte[] data, String contentType, boolean chunked) throws Exception {
        OutputStream o = s.getOutputStream();
        StringBuilder h = new StringBuilder();
        h.append("HTTP/1.1 200 OK\r\n");
        h.append("Content-Type: ").append(contentType).append("\r\n");
        h.append("Content-Length: ").append(data.length).append("\r\n");
        h.append("Connection: close\r\n\r\n");
        o.write(h.toString().getBytes("UTF-8"));
        o.write(data);
        o.flush();
    }
}
