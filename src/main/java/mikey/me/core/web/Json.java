package mikey.me.core.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {

    private Json() {
    }

    public static Value parse(String raw) {
        if (raw == null) {
            return null;
        }
        Parser parser = new Parser(raw);
        try {
            Value value = parser.value();
            if (!parser.end()) {
                return null;
            }
            return value;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static String quote(String text) {
        if (text == null) {
            return "\"\"";
        }
        StringBuilder out = new StringBuilder(text.length() + 2);
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        out.append('"');
        return out.toString();
    }

    public static final class Value {

        private final Object raw;

        private Value(Object raw) {
            this.raw = raw;
        }

        static Value of(Object raw) {
            return new Value(raw);
        }

        public boolean isObject() {
            return raw instanceof Map;
        }

        public boolean isArray() {
            return raw instanceof List;
        }

        public boolean isNull() {
            return raw == null;
        }

        public boolean has(String key) {
            return isObject() && object().containsKey(key);
        }

        public Value get(String key) {
            if (!isObject()) {
                return null;
            }
            return object().get(key);
        }

        @SuppressWarnings("unchecked")
        public Map<String, Value> object() {
            return raw instanceof Map<?, ?> map ? (Map<String, Value>) map : Map.of();
        }

        @SuppressWarnings("unchecked")
        public List<Value> array() {
            return raw instanceof List<?> list ? (List<Value>) list : List.of();
        }

        public String text() {
            return raw instanceof String text ? text : null;
        }

        public Double number() {
            return raw instanceof Double number ? number : null;
        }

        public Boolean bool() {
            return raw instanceof Boolean flag ? flag : null;
        }
    }

    private static final class Parser {

        private final String raw;
        private int i;
        private int depth;

        private Parser(String raw) {
            this.raw = raw;
        }

        private boolean end() {
            skip();
            return i >= raw.length();
        }

        private Value value() {
            skip();
            if (i >= raw.length()) {
                throw bad();
            }
            char c = raw.charAt(i);
            if (c == '{') {
                return object();
            }
            if (c == '[') {
                return array();
            }
            if (c == '"') {
                return Value.of(string());
            }
            if (c == 't' || c == 'f') {
                return bool();
            }
            if (c == 'n') {
                return nul();
            }
            if (c == '-' || (c >= '0' && c <= '9')) {
                return number();
            }
            throw bad();
        }

        private Value object() {
            i++;
            down();
            Map<String, Value> map = new LinkedHashMap<>();
            skip();
            if (eat('}')) {
                up();
                return Value.of(map);
            }
            while (true) {
                skip();
                if (i >= raw.length() || raw.charAt(i) != '"') {
                    throw bad();
                }
                String key = string();
                skip();
                if (!eat(':')) {
                    throw bad();
                }
                map.put(key, value());
                skip();
                if (eat('}')) {
                    up();
                    return Value.of(map);
                }
                if (!eat(',')) {
                    throw bad();
                }
            }
        }

        private Value array() {
            i++;
            down();
            List<Value> list = new ArrayList<>();
            skip();
            if (eat(']')) {
                up();
                return Value.of(list);
            }
            while (true) {
                list.add(value());
                skip();
                if (eat(']')) {
                    up();
                    return Value.of(list);
                }
                if (!eat(',')) {
                    throw bad();
                }
            }
        }

        private String string() {
            if (!eat('"')) {
                throw bad();
            }
            StringBuilder out = new StringBuilder();
            while (i < raw.length()) {
                char c = raw.charAt(i++);
                if (c == '"') {
                    return out.toString();
                }
                if (c == '\\') {
                    if (i >= raw.length()) {
                        throw bad();
                    }
                    char escaped = raw.charAt(i++);
                    switch (escaped) {
                        case '"', '\\', '/' -> out.append(escaped);
                        case 'b' -> out.append('\b');
                        case 'f' -> out.append('\f');
                        case 'n' -> out.append('\n');
                        case 'r' -> out.append('\r');
                        case 't' -> out.append('\t');
                        case 'u' -> {
                            if (i + 4 > raw.length()) {
                                throw bad();
                            }
                            int code;
                            try {
                                code = Integer.parseInt(raw.substring(i, i + 4), 16);
                            } catch (NumberFormatException e) {
                                throw bad();
                            }
                            out.append((char) code);
                            i += 4;
                        }
                        default -> throw bad();
                    }
                } else {
                    if (c < 0x20) {
                        throw bad();
                    }
                    out.append(c);
                }
            }
            throw bad();
        }

        private Value bool() {
            if (raw.startsWith("true", i) && wordEnd(i + 4)) {
                i += 4;
                return Value.of(Boolean.TRUE);
            }
            if (raw.startsWith("false", i) && wordEnd(i + 5)) {
                i += 5;
                return Value.of(Boolean.FALSE);
            }
            throw bad();
        }

        private Value nul() {
            if (raw.startsWith("null", i) && wordEnd(i + 4)) {
                i += 4;
                return Value.of(null);
            }
            throw bad();
        }

        private Value number() {
            int start = i;
            if (raw.charAt(i) == '-') {
                i++;
            }
            if (i >= raw.length() || raw.charAt(i) < '0' || raw.charAt(i) > '9') {
                throw bad();
            }
            if (raw.charAt(i) == '0') {
                i++;
            } else {
                while (i < raw.length() && raw.charAt(i) >= '0' && raw.charAt(i) <= '9') {
                    i++;
                }
            }
            if (i < raw.length() && raw.charAt(i) == '.') {
                i++;
                int digits = i;
                while (i < raw.length() && raw.charAt(i) >= '0' && raw.charAt(i) <= '9') {
                    i++;
                }
                if (i == digits) {
                    throw bad();
                }
            }
            if (i < raw.length() && (raw.charAt(i) == 'e' || raw.charAt(i) == 'E')) {
                i++;
                if (i < raw.length() && (raw.charAt(i) == '+' || raw.charAt(i) == '-')) {
                    i++;
                }
                int digits = i;
                while (i < raw.length() && raw.charAt(i) >= '0' && raw.charAt(i) <= '9') {
                    i++;
                }
                if (i == digits) {
                    throw bad();
                }
            }
            try {
                double value = Double.parseDouble(raw.substring(start, i));
                if (!Double.isFinite(value)) {
                    throw bad();
                }
                return Value.of(value);
            } catch (NumberFormatException e) {
                throw bad();
            }
        }

        private boolean wordEnd(int at) {
            if (at >= raw.length()) {
                return true;
            }
            char c = raw.charAt(at);
            return c == ',' || c == '}' || c == ']' || c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == ':';
        }

        private boolean eat(char c) {
            if (i < raw.length() && raw.charAt(i) == c) {
                i++;
                return true;
            }
            return false;
        }

        private void skip() {
            while (i < raw.length()) {
                char c = raw.charAt(i);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                    return;
                }
                i++;
            }
        }

        private void down() {
            if (++depth > 32) {
                throw bad();
            }
        }

        private void up() {
            depth--;
        }

        private IllegalArgumentException bad() {
            return new IllegalArgumentException("bad json");
        }
    }
}
