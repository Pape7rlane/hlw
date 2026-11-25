package com.hlw.srv;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 极简 JSON（对象=LinkedHashMap，数组=ArrayList），够用于存档与协议。 */
public final class Json {

    private Json() {}

    // ───────── 解析 ─────────
    public static Object parse(String s) {
        P p = new P(s);
        p.ws();
        Object v = p.value();
        p.ws();
        return v;
    }

    private static final class P {
        final String s; int i;
        P(String s) { this.s = s; }
        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        char peek() { return i < s.length() ? s.charAt(i) : '\0'; }
        void expect(char c) { if (peek() == c) i++; }

        Object value() {
            ws();
            char c = peek();
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            return num();
        }

        Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            expect('{'); ws();
            if (peek() == '}') { i++; return m; }
            while (i < s.length()) {
                ws();
                String k = str();
                ws(); expect(':'); ws();
                Object v = value();
                m.put(k, v);
                ws();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == '}') { i++; break; }
            }
            return m;
        }

        List<Object> arr() {
            List<Object> a = new ArrayList<Object>();
            expect('['); ws();
            if (peek() == ']') { i++; return a; }
            while (i < s.length()) {
                a.add(value());
                ws();
                char c = peek();
                if (c == ',') { i++; continue; }
                if (c == ']') { i++; break; }
            }
            return a;
        }

        String str() {
            StringBuilder sb = new StringBuilder();
            expect('"');
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') break;
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n': sb.append('\n'); break;
                        case 't': sb.append('\t'); break;
                        case 'r': sb.append('\r'); break;
                        case 'b': sb.append('\b'); break;
                        case 'f': sb.append('\f'); break;
                        case 'u':
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4; break;
                        default: sb.append(e);
                    }
                } else sb.append(c);
            }
            return sb.toString();
        }

        Object num() {
            int st = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            String t = s.substring(st, i);
            if (t.isEmpty()) return null;
            try {
                if (t.indexOf('.') >= 0 || t.indexOf('e') >= 0 || t.indexOf('E') >= 0) return Double.valueOf(t);
                long l = Long.parseLong(t);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) return Integer.valueOf((int) l);
                return Long.valueOf(l);
            } catch (Exception e) { return null; }
        }
    }

    // ───────── 序列化 ─────────
    public static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        w(o, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void w(Object o, StringBuilder sb) {
        if (o == null) { sb.append("null"); return; }
        if (o instanceof String) { esc((String) o, sb); return; }
        if (o instanceof Boolean || o instanceof Number) { sb.append(o.toString()); return; }
        if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                esc(e.getKey(), sb);
                sb.append(':');
                w(e.getValue(), sb);
            }
            sb.append('}');
            return;
        }
        if (o instanceof List) {
            sb.append('[');
            boolean first = true;
            for (Object x : (List<Object>) o) {
                if (!first) sb.append(',');
                first = false;
                w(x, sb);
            }
            sb.append(']');
            return;
        }
        esc(String.valueOf(o), sb);
    }

    private static void esc(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    // ───────── 便捷取值 ─────────
    @SuppressWarnings("unchecked")
    public static Map<String, Object> obj(Object o) {
        return (o instanceof Map) ? (Map<String, Object>) o : new LinkedHashMap<String, Object>();
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Object o) {
        return (o instanceof List) ? (List<Object>) o : new ArrayList<Object>();
    }

    public static int i(Object o, int d) {
        if (o instanceof Number) return ((Number) o).intValue();
        if (o instanceof String) { try { return (int) Double.parseDouble((String) o); } catch (Exception e) { return d; } }
        return d;
    }

    public static long l(Object o, long d) {
        if (o instanceof Number) return ((Number) o).longValue();
        if (o instanceof String) { try { return Long.parseLong((String) o); } catch (Exception e) { return d; } }
        return d;
    }

    public static String s(Object o, String d) {
        return (o == null) ? d : String.valueOf(o);
    }

    /** 按 "a.b.c" 取子节点 */
    @SuppressWarnings("unchecked")
    public static Object path(Object root, String dotted) {
        Object cur = root;
        for (String k : dotted.split("\\.")) {
            if (!(cur instanceof Map)) return null;
            cur = ((Map<String, Object>) cur).get(k);
            if (cur == null) return null;
        }
        return cur;
    }
}
