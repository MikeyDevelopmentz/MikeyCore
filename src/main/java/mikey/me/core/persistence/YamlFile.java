package mikey.me.core.persistence;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class YamlFile {

    // yaml 1.1 reserved words that read as non-strings, matched case-insensitively
    private static final Set<String> RESERVED = Set.of(
            "true", "false", "yes", "no", "on", "off", "y", "n", "null", "nan", ".nan", "-.nan", ".inf", "-.inf");

    // chars that can't lead a plain scalar, quoting is always safe so just reject broadly
    private static final String UNSAFE_LEAD = "|>%@`?~,#{}[]&*!\"'<=";

    private YamlFile() {
    }

    public static Map<String, Object> read(Path path) throws IOException {
        if (path == null || !Files.exists(path)) {
            return new LinkedHashMap<>();
        }
        return new Parser(Files.readString(path)).parseRoot();
    }

    public static void write(Path path, Map<String, Object> root) throws IOException {
        if (path == null) {
            throw new IOException("path is null");
        }
        // bare relative name has a null parent, createTempFile rejects that, so go absolute
        // and the temp file lands next to the target on the same filesystem
        if (path.getParent() == null) {
            path = path.toAbsolutePath();
        }
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // unique temp name per write, a fixed .tmp sibling gets spliced by concurrent writers
        String prefix = path.getFileName().toString();
        if (prefix.length() < 3) {
            prefix = (prefix + "___").substring(0, 3);
        }
        Path tmp = Files.createTempFile(parent, prefix, ".tmp");
        boolean moved = false;
        try {
            Files.writeString(tmp, write(root == null ? Map.of() : root));
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(tmp);
            }
        }
    }

    public static String string(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    public static double number(Map<String, Object> map, String key, double fallback) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return fallback;
    }

    public static int integer(Map<String, Object> map, String key, int fallback) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return fallback;
    }

    public static boolean bool(Map<String, Object> map, String key, boolean fallback) {
        Object value = map == null ? null : map.get(key);
        return value instanceof Boolean flag ? flag : fallback;
    }

    public static List<String> strings(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object item : list) {
            out.add(item == null ? "" : String.valueOf(item));
        }
        return Collections.unmodifiableList(out);
    }

    // return an immutable view of the nested map, the live map let callers mutate the doc
    public static Map<String, Object> map(Map<String, Object> map, String key) {
        Object value = map == null ? null : map.get(key);
        if (value instanceof Map<?, ?> nested) {
            return Collections.unmodifiableMap(cast(nested));
        }
        return Map.of();
    }

    static String write(Map<String, Object> root) throws IOException {
        StringBuilder out = new StringBuilder();
        writeMap(out, root, 0);
        return out.toString();
    }

    private static void writeMap(StringBuilder out, Map<String, Object> map, int indent) throws IOException {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            pad(out, indent);
            out.append(renderKey(entry.getKey())).append(':');
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                Map<String, Object> child = cast(nested);
                if (child.isEmpty()) {
                    // bare "key:" reads back as "" since we cant tell empty section from no section
                    // "{}" is the unambiguous one
                    out.append(" {}\n");
                } else {
                    out.append('\n');
                    writeMap(out, child, indent + 2);
                }
            } else if (value instanceof List<?> list) {
                if (list.isEmpty()) {
                    out.append(" []\n");
                    continue;
                }
                out.append('\n');
                for (Object item : list) {
                    if (item instanceof Map || item instanceof List) {
                        throw new IOException("lists can only hold plain values");
                    }
                    pad(out, indent + 2);
                    out.append("- ").append(renderScalar(item)).append('\n');
                }
            } else {
                out.append(' ').append(renderScalar(value)).append('\n');
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Map<?, ?> map) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            copy.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return copy;
    }

    private static void pad(StringBuilder out, int indent) {
        out.append(" ".repeat(Math.max(0, indent)));
    }

    private static String renderKey(String key) throws IOException {
        if (key == null || key.isBlank() || key.indexOf('\n') >= 0 || key.indexOf(':') >= 0) {
            throw new IOException("bad key");
        }
        // quotes in keys break the ':' split, so quote the key back
        if (key.indexOf('"') >= 0 || key.indexOf('\'') >= 0) {
            return "'" + key.replace("'", "''") + "'";
        }
        return key;
    }

    private static String renderScalar(Object value) {
        if (value == null) {
            // bare "null" stays distinct from '' so it survives a round trip
            return "null";
        }
        if (value instanceof Boolean || value instanceof Integer || value instanceof Long
                || value instanceof Short || value instanceof Byte
                || value instanceof BigInteger || value instanceof BigDecimal) {
            return String.valueOf(value);
        }
        if (value instanceof Double number) {
            if (number.isNaN() || number.isInfinite()) {
                return quote(String.valueOf(number));
            }
            return Double.toString(number);
        }
        if (value instanceof Float number) {
            if (number.isNaN() || number.isInfinite()) {
                return quote(String.valueOf(number));
            }
            return Float.toString(number);
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String text) {
        if (plain(text)) {
            return text;
        }
        return "'" + text.replace("'", "''") + "'";
    }

    private static boolean plain(String text) {
        if (text.isEmpty() || Character.isWhitespace(text.charAt(0)) || Character.isWhitespace(text.charAt(text.length() - 1))) {
            return false;
        }
        if (RESERVED.contains(text.toLowerCase(java.util.Locale.ROOT))) {
            return false;
        }
        if (UNSAFE_LEAD.indexOf(text.charAt(0)) >= 0) {
            return false;
        }
        if (text.indexOf(':') >= 0 || text.indexOf('#') >= 0 || text.indexOf('\n') >= 0) {
            return false;
        }
        return !isNumber(text);
    }

    private static boolean isNumber(String text) {
        if (text.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(text);
            return true;
        } catch (NumberFormatException e) {
            // parseDouble rejects hex/octal/binary but yaml 1.1 accepts them, quote anything not plain decimal
            return !HEX.matcher(text).matches()
                    && !NON_DECIMAL.matcher(text).matches()
                    && !SEXAGESIMAL.matcher(text).matches();
        }
    }

    private static final java.util.regex.Pattern HEX =
            java.util.regex.Pattern.compile("^[+-]?0[xX][0-9a-fA-F_]+$");
    private static final java.util.regex.Pattern NON_DECIMAL =
            java.util.regex.Pattern.compile("^[+-]?0[bBoO][0-9a-fA-F_]+$");
    private static final java.util.regex.Pattern SEXAGESIMAL =
            java.util.regex.Pattern.compile("^[+-]?[0-9][0-9_]*(:[0-5]?[0-9])+(\\.[0-9_]*)?$");

    private static final class Parser {
        private final String[] lines;
        private int index;
        private Row pending;

        private Parser(String text) {
            this.lines = text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        }

        private Map<String, Object> parseRoot() throws IOException {
            Map<String, Object> root = parseMap(0);
            Row extra = peek();
            if (extra != null) {
                throw error("leftover line");
            }
            return root;
        }

        private Map<String, Object> parseMap(int indent) throws IOException {
            Map<String, Object> map = new LinkedHashMap<>();
            while (true) {
                Row row = peek();
                if (row == null || row.indent < indent || row.list) {
                    break;
                }
                if (row.indent > indent) {
                    throw error("bad indent");
                }
                consume();
                int colon = split(row.content);
                if (colon <= 0) {
                    throw error("expected key");
                }
                String key = unquote(row.content.substring(0, colon).trim());
                String rest = row.content.substring(colon + 1).trim();
                if ("{}".equals(rest)) {
                    map.put(key, new LinkedHashMap<String, Object>());
                    continue;
                }
                if ("[]".equals(rest)) {
                    map.put(key, new ArrayList<>());
                    continue;
                }
                if (!rest.isEmpty()) {
                    map.put(key, scalar(rest));
                    continue;
                }
                Row next = peek();
                if (next != null && next.list && next.indent >= indent) {
                    map.put(key, parseList(next.indent));
                } else if (next == null || next.indent <= indent) {
                    // bare "key:" with no value, "" is the closest thing; "{}" for a real empty map
                    map.put(key, "");
                } else {
                    map.put(key, parseMap(next.indent));
                }
            }
            return map;
        }

        private List<Object> parseList(int indent) throws IOException {
            List<Object> list = new ArrayList<>();
            while (true) {
                Row row = peek();
                if (row == null || !row.list || row.indent != indent) {
                    break;
                }
                consume();
                if (!row.listValue.isEmpty()) {
                    list.add(scalar(row.listValue));
                    continue;
                }
                Row next = peek();
                if (next != null && next.indent > indent && !next.list) {
                    list.add(parseMap(next.indent));
                } else if (next == null) {
                    list.add("");
                } else {
                    // throw instead of substituting "", it hid hand-edited or truncated lists
                    throw error("bad list item");
                }
            }
            return list;
        }

        private Row peek() throws IOException {
            if (pending != null) {
                return pending;
            }
            while (index < lines.length) {
                String raw = lines[index++];
                if (raw.isBlank()) {
                    continue;
                }
                int indent = 0;
                while (indent < raw.length() && raw.charAt(indent) == ' ') {
                    indent++;
                }
                if (indent < raw.length() && raw.charAt(indent) == '\t') {
                    throw error("tabs arent allowed");
                }
                String content = raw.substring(indent);
                if (content.startsWith("#")) {
                    continue;
                }
                boolean list = content.equals("-") || content.startsWith("- ");
                String listValue = content.equals("-") ? "" : list ? content.substring(2).trim() : "";
                pending = new Row(indent, content, list, listValue);
                return pending;
            }
            return null;
        }

        private void consume() {
            pending = null;
        }

        private int split(String content) {
            boolean quoted = false;
            char quote = 0;
            for (int i = 0; i < content.length(); i++) {
                char c = content.charAt(i);
                if (quoted) {
                    if (c == quote) {
                        quoted = false;
                    }
                    continue;
                }
                if (c == '\'' || c == '"') {
                    quoted = true;
                    quote = c;
                    continue;
                }
                if (c == ':') {
                    return i;
                }
            }
            return -1;
        }

        private Object scalar(String raw) throws IOException {
            String text = raw.trim();
            if (text.isEmpty()) {
                return "";
            }
            if ("null".equals(text) || "~".equals(text)) {
                return null;
            }
            if ((text.startsWith("'") && text.endsWith("'")) || (text.startsWith("\"") && text.endsWith("\""))) {
                return unquote(text);
            }
            if ("true".equals(text)) {
                return true;
            }
            if ("false".equals(text)) {
                return false;
            }
            if (text.indexOf('.') >= 0 || text.indexOf('e') >= 0 || text.indexOf('E') >= 0) {
                try {
                    return Double.parseDouble(text);
                } catch (NumberFormatException ignored) {
                    return text;
                }
            }
            try {
                long value = Long.parseLong(text);
                if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
                    return (int) value;
                }
                return value;
            } catch (NumberFormatException ignored) {
                return text;
            }
        }

        private String unquote(String text) throws IOException {
            if (text.length() < 2) {
                return text;
            }
            char quote = text.charAt(0);
            if ((quote != '\'' && quote != '"') || text.charAt(text.length() - 1) != quote) {
                return text;
            }
            String body = text.substring(1, text.length() - 1);
            if (quote == '\'') {
                return body.replace("''", "'");
            }
            StringBuilder out = new StringBuilder(body.length());
            for (int i = 0; i < body.length(); i++) {
                char c = body.charAt(i);
                if (c != '\\' || i + 1 >= body.length()) {
                    out.append(c);
                    continue;
                }
                char next = body.charAt(++i);
                switch (next) {
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case '\\', '"', '\'' -> out.append(next);
                    default -> throw error("bad escape");
                }
            }
            return out.toString();
        }

        private IOException error(String message) {
            return new IOException(message + " at line " + Math.max(1, index));
        }
    }

    private record Row(int indent, String content, boolean list, String listValue) {
    }
}
