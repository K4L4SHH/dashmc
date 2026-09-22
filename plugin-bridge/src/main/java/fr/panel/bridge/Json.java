package fr.panel.bridge;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mini utilitaire JSON : pas de dépendance externe, volontairement simple.
 * Suffisant pour des payloads plats {"cle":"valeur", "cle2":123, "cle3":true}.
 */
public final class Json {

    private Json() {}

    // ---- Écriture ----

    public static String write(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(quote(e.getKey())).append(':').append(writeValue(e.getValue()));
        }
        return sb.append('}').toString();
    }

    @SuppressWarnings("unchecked")
    private static String writeValue(Object v) {
        if (v == null) return "null";
        if (v instanceof String) return quote((String) v);
        if (v instanceof Boolean || v instanceof Number) return v.toString();
        if (v instanceof Map) return write((Map<String, Object>) v);
        if (v instanceof List) {
            StringBuilder sb = new StringBuilder("[");
            List<?> list = (List<?>) v;
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) sb.append(',');
                sb.append(writeValue(list.get(i)));
            }
            return sb.append(']').toString();
        }
        return quote(v.toString());
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
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
        return sb.append('"').toString();
    }

    // ---- Lecture (JSON plat uniquement) ----

    public static Map<String, String> parseFlat(String body) {
        Map<String, String> out = new LinkedHashMap<>();
        if (body == null) return out;
        String s = body.trim();
        if (s.startsWith("{")) s = s.substring(1);
        if (s.endsWith("}")) s = s.substring(0, s.length() - 1);
        int i = 0, n = s.length();
        while (i < n) {
            while (i < n && (s.charAt(i) == ' ' || s.charAt(i) == ',' || s.charAt(i) == '\n' || s.charAt(i) == '\r')) i++;
            if (i >= n) break;
            if (s.charAt(i) != '"') break;
            int[] posKey = new int[1];
            String key = readString(s, i, posKey);
            i = posKey[0];
            while (i < n && (s.charAt(i) == ' ' || s.charAt(i) == ':')) i++;
            String value;
            if (i < n && s.charAt(i) == '"') {
                int[] posVal = new int[1];
                value = readString(s, i, posVal);
                i = posVal[0];
            } else {
                int start = i;
                while (i < n && s.charAt(i) != ',' ) i++;
                value = s.substring(start, i).trim();
            }
            out.put(key, value);
        }
        return out;
    }

    private static String readString(String s, int start, int[] endPos) {
        int i = start + 1; // saute le guillemet ouvrant
        StringBuilder sb = new StringBuilder();
        while (i < s.length() && s.charAt(i) != '"') {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    default: sb.append(next);
                }
                i += 2;
            } else {
                sb.append(c);
                i++;
            }
        }
        endPos[0] = i + 1; // après le guillemet fermant
        return sb.toString();
    }
}
