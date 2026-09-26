package mikey.me.core.json;

public final class JsonUtil {

    private JsonUtil() {}

    public static String escape(String value) {
        if (value == null) return "";
        StringBuilder sb = new StringBuilder(value.length() + 2);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '"':  sb.append("\\\""); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.toString();
    }

    public static String extractString(String json, String key) {
        if (json == null || key == null) return "";
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return "";
        start += search.length();
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                switch (c) {
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    case '"':  sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'u':
                        if (i + 4 < json.length()) {
                            try {
                                sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException ignored) {}
                        }
                        break;
                    default:  sb.append(c);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                return sb.toString();
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static boolean extractBool(String json, String key, boolean def) {
        if (json == null || key == null) return def;
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return def;
        start += search.length();
        if (json.regionMatches(start, "true", 0, 4) && hasValueBoundary(json, start + 4)) return true;
        if (json.regionMatches(start, "false", 0, 5) && hasValueBoundary(json, start + 5)) return false;
        return def;
    }

    public static boolean hasKey(String json, String key) {
        return json != null && key != null && json.indexOf("\"" + key + "\":") >= 0;
    }

    public static long extractLong(String json, String key, long def) {
        if (json == null || key == null) return def;
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return def;
        start += search.length();
        if (start >= json.length()) return def;
        int end = start;
        if (json.charAt(end) == '-') end++;
        int digits = end;
        while (end < json.length() && json.charAt(end) >= '0' && json.charAt(end) <= '9') end++;
        if (end == digits || !hasValueBoundary(json, end)) return def;
        try { return Long.parseLong(json.substring(start, end)); }
        catch (NumberFormatException e) { return def; }
    }

    public static int extractInt(String json, String key, int def) {
        long value = extractLong(json, key, def);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) return def;
        return (int) value;
    }

    private static boolean hasValueBoundary(String value, int end) {
        int index = end;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
        return index == value.length() || value.charAt(index) == ',' || value.charAt(index) == '}' || value.charAt(index) == ']';
    }
}
