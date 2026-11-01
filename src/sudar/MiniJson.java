package sudar;

import java.util.*;

/** Tiny JSON reader/writer (no external library needed). Objects -> LinkedHashMap, arrays -> ArrayList, numbers -> Double. */
public final class MiniJson {
    private final String s; private int p = 0;
    private MiniJson(String s) { this.s = s; }

    public static Object parse(String text) {
        MiniJson j = new MiniJson(text); j.ws(); Object v = j.value(); j.ws();
        if (j.p != j.s.length()) throw new IllegalArgumentException("Unexpected data at position " + j.p);
        return v;
    }

    private void ws() { while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++; }
    private char peek() { return s.charAt(p); }
    private void expect(char c) { if (p >= s.length() || s.charAt(p) != c) throw new IllegalArgumentException("Expected '" + c + "' at " + p); p++; }

    private Object value() {
        ws(); char c = peek();
        if (c == '{') return object();
        if (c == '[') return array();
        if (c == '"') return string();
        if (s.startsWith("true", p)) { p += 4; return Boolean.TRUE; }
        if (s.startsWith("false", p)) { p += 5; return Boolean.FALSE; }
        if (s.startsWith("null", p)) { p += 4; return null; }
        int st = p;
        while (p < s.length() && "+-0123456789.eE".indexOf(s.charAt(p)) >= 0) p++;
        return Double.parseDouble(s.substring(st, p));
    }
    private Map<String, Object> object() {
        Map<String, Object> m = new LinkedHashMap<>(); expect('{'); ws();
        if (peek() == '}') { p++; return m; }
        while (true) { ws(); String k = string(); ws(); expect(':'); m.put(k, value()); ws();
            if (peek() == ',') { p++; continue; } expect('}'); return m; }
    }
    private List<Object> array() {
        List<Object> l = new ArrayList<>(); expect('['); ws();
        if (peek() == ']') { p++; return l; }
        while (true) { l.add(value()); ws(); if (peek() == ',') { p++; continue; } expect(']'); return l; }
    }
    private String string() {
        expect('"'); StringBuilder b = new StringBuilder();
        while (peek() != '"') {
            char c = s.charAt(p++);
            if (c == '\\') {
                char n = s.charAt(p++);
                switch (n) { case 'n': b.append('\n'); break; case 't': b.append('\t'); break; case 'r': b.append('\r'); break;
                    case 'b': b.append('\b'); break; case 'f': b.append('\f'); break;
                    case 'u': b.append((char) Integer.parseInt(s.substring(p, p + 4), 16)); p += 4; break;
                    default: b.append(n); }
            } else b.append(c);
        }
        p++; return b.toString();
    }

    // ---- writer ----
    public static String write(Object o) { StringBuilder b = new StringBuilder(); write(o, b); return b.toString(); }
    @SuppressWarnings("unchecked")
    private static void write(Object o, StringBuilder b) {
        if (o == null) b.append("null");
        else if (o instanceof String) quote((String) o, b);
        else if (o instanceof Boolean) b.append(o);
        else if (o instanceof Number) {
            double d = ((Number) o).doubleValue();
            if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d); else b.append(d);
        } else if (o instanceof Map) {
            b.append('{'); boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                if (!first) b.append(','); first = false; quote(e.getKey(), b); b.append(':'); write(e.getValue(), b);
            }
            b.append('}');
        } else if (o instanceof Collection) {
            b.append('['); boolean first = true;
            for (Object x : (Collection<Object>) o) { if (!first) b.append(','); first = false; write(x, b); }
            b.append(']');
        } else quote(o.toString(), b);
    }
    private static void quote(String s, StringBuilder b) {
        b.append('"');
        for (char c : s.toCharArray()) {
            switch (c) { case '"': b.append("\\\""); break; case '\\': b.append("\\\\"); break; case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break; case '\t': b.append("\\t"); break;
                default: if (c < 0x20) b.append(String.format("\\u%04x", (int) c)); else b.append(c); }
        }
        b.append('"');
    }
}
