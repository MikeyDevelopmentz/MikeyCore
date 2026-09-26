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
        int start = findValueStart(json, key);
        if (start < 0) return "";
        JsonString value = readString(json, start);
        return value != null && hasValueBoundary(json, value.end()) ? value.value() : "";
    }

    private static JsonString readString(String json, int start) {
        if (start >= json.length() || json.charAt(start) != '"') return null;
        StringBuilder sb = new StringBuilder();
        boolean escape = false;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) {
                switch (c) {
                    case 'b':  sb.append('\b'); break;
                    case 'f':  sb.append('\f'); break;
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
                            } catch (NumberFormatException ignored) { return null; }
                        } else {
                            return null;
                        }
                        break;
                    default:  return null;
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '"') {
                return new JsonString(sb.toString(), i + 1);
            } else {
                if (c < 0x20) return null;
                sb.append(c);
            }
        }
        return null;
    }

    public static boolean extractBool(String json, String key, boolean def) {
        int start = findValueStart(json, key);
        if (start < 0) return def;
        if (json.regionMatches(start, "true", 0, 4) && hasValueBoundary(json, start + 4)) return true;
        if (json.regionMatches(start, "false", 0, 5) && hasValueBoundary(json, start + 5)) return false;
        return def;
    }

    public static boolean hasKey(String json, String key) {
        return findValueStart(json, key) >= 0;
    }

    public static long extractLong(String json, String key, long def) {
        int start = findValueStart(json, key);
        if (start < 0) return def;
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

    private static int findValueStart(String json, String key) {
        if (json == null || key == null) return -1;
        int start = skipWhitespace(json, 0);
        if (start >= json.length() || json.charAt(start) != '{') return -1;
        int depth = 1;
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                JsonString string = readString(json, i);
                if (string == null) return -1;
                int next = skipWhitespace(json, string.end());
                if (depth == 1 && string.value().equals(key)
                        && next < json.length() && json.charAt(next) == ':') {
                    int value = skipWhitespace(json, next + 1);
                    return value < json.length() ? value : -1;
                }
                i = string.end() - 1;
            } else if (c == '{' || c == '[') {
                depth++;
            } else if (c == '}' || c == ']') {
                if (--depth == 0) return -1;
            }
        }
        return -1;
    }

    private static int skipWhitespace(String json, int start) {
        while (start < json.length()) {
            char c = json.charAt(start);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') break;
            start++;
        }
        return start;
    }

    private record JsonString(String value, int end) {}

    private static boolean hasValueBoundary(String value, int end) {
        int index = end;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
        return index == value.length() || value.charAt(index) == ',' || value.charAt(index) == '}' || value.charAt(index) == ']';
    }
}
