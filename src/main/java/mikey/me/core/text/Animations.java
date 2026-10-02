package mikey.me.core.text;

import java.awt.Color;
import java.util.List;
import java.util.Locale;

public final class Animations {

    private static final String LEGACY = "0123456789abcdef";
    private static final int[] LEGACY_RGB = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA,
            0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF,
            0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    private Animations() {
    }

    public static boolean animated(String text) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("&u") || lower.contains("<rainbow") || lower.contains("<#anim:");
    }

    public static boolean any(List<String> lines) {
        if (lines == null) {
            return false;
        }
        for (String line : lines) {
            if (animated(line)) {
                return true;
            }
        }
        return false;
    }

    public static String frame(String text, int tick) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        if (!animated(text)) {
            return text;
        }
        return rainbowAmp(replaceTags(text, tick), tick);
    }

    private static String replaceTags(String text, int tick) {
        StringBuilder out = new StringBuilder(text.length());
        String lower = text.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < text.length()) {
            int anim = lower.indexOf("<#anim:", i);
            int rainbow = lower.indexOf("<rainbow", i);
            int next = earliest(anim, rainbow);
            if (next < 0) {
                out.append(text, i, text.length());
                break;
            }
            out.append(text, i, next);
            if (next == anim) {
                int headerEnd = text.indexOf('>', next);
                int close = lower.indexOf("</#anim>", headerEnd < 0 ? next : headerEnd);
                if (headerEnd < 0 || close < 0) {
                    out.append(text.charAt(next));
                    i = next + 1;
                    continue;
                }
                String header = text.substring(next + 7, headerEnd);
                String inner = text.substring(headerEnd + 1, close);
                out.append(animFrame(header, inner, tick));
                i = close + 8;
            } else {
                int headerEnd = text.indexOf('>', next);
                int close = lower.indexOf("</rainbow>", headerEnd < 0 ? next : headerEnd);
                if (headerEnd < 0 || close < 0) {
                    out.append(text.charAt(next));
                    i = next + 1;
                    continue;
                }
                int offset = number(text.substring(next + 8, headerEnd));
                out.append(paint(text.substring(headerEnd + 1, close), tick + offset, Animations::hue));
                i = close + 10;
            }
        }
        return out.toString();
    }

    private static String animFrame(String header, String inner, int tick) {
        String name = header;
        String args = "";
        int colon = header.indexOf(':');
        if (colon >= 0) {
            name = header.substring(0, colon);
            args = header.substring(colon + 1);
        }
        String kind = name.toLowerCase(Locale.ROOT);
        return switch (kind) {
            case "wave" -> twoColor(inner, tick, args, true);
            case "burn" -> twoColor(inner, tick, args, false);
            case "typewriter" -> typewriter(inner, tick);
            case "scroll" -> scroll(inner, tick);
            default -> paint(inner, tick, Animations::hue);
        };
    }

    private static String twoColor(String inner, int tick, String args, boolean wave) {
        int[] colors = colors(args);
        int visible = visible(inner);
        if (visible == 0) {
            return inner;
        }
        int mark = Math.floorMod(tick, visible + (wave ? 0 : 6));
        StringBuilder out = new StringBuilder();
        int index = 0;
        for (int i = 0; i < inner.length(); i++) {
            if (format(inner, i)) {
                out.append(inner, i, i + 2);
                i++;
                continue;
            }
            int codePoint = inner.codePointAt(i);
            if (codePoint == '\n') {
                out.append('\n');
                i += Character.charCount(codePoint) - 1;
                continue;
            }
            boolean hot = wave ? index == mark || index == Math.floorMod(mark + 1, visible) : index < mark;
            out.append(amp(hot ? colors[1] : colors[0])).appendCodePoint(codePoint);
            index++;
            i += Character.charCount(codePoint) - 1;
        }
        return out.toString();
    }

    private static String typewriter(String inner, int tick) {
        int visible = visible(inner);
        int shown = Math.floorMod(tick, visible + 8);
        if (shown > visible) {
            shown = visible;
        }
        StringBuilder out = new StringBuilder();
        int index = 0;
        for (int i = 0; i < inner.length() && index < shown; i++) {
            if (format(inner, i)) {
                out.append(inner, i, i + 2);
                i++;
                continue;
            }
            out.append(inner.charAt(i));
            if (inner.charAt(i) != '\n') {
                index++;
            }
        }
        return out.toString();
    }

    private static String scroll(String inner, int tick) {
        int width = 16;
        String loop = " ".repeat(width) + inner + " ".repeat(width);
        int start = Math.floorMod(tick, Math.max(1, inner.length() + width));
        int end = Math.min(loop.length(), start + width);
        return loop.substring(start, end);
    }

    // iterate by code point like Colors, splitting a surrogate pair mangles the emoji
    private static String paint(String inner, int tick, Hue hue) {
        StringBuilder out = new StringBuilder();
        int index = 0;
        for (int i = 0; i < inner.length(); i++) {
            if (format(inner, i)) {
                out.append(inner, i, i + 2);
                i++;
                continue;
            }
            int codePoint = inner.codePointAt(i);
            if (codePoint == '\n') {
                out.append('\n');
                i += Character.charCount(codePoint) - 1;
                continue;
            }
            out.append(amp(hue.rgb(tick, index))).appendCodePoint(codePoint);
            index++;
            i += Character.charCount(codePoint) - 1;
        }
        return out.toString();
    }

    // &u colors the rest until a normal color code
    private static String rainbowAmp(String text, int tick) {
        if (!text.toLowerCase(Locale.ROOT).contains("&u")) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length());
        boolean on = false;
        int index = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '&' && i + 1 < text.length()) {
                char code = text.charAt(i + 1);
                if (code == 'u' || code == 'U') {
                    on = true;
                    i++;
                    continue;
                }
                if (LEGACY.indexOf(Character.toLowerCase(code)) >= 0) {
                    on = false;
                }
            }
            if (text.charAt(i) == '<') {
                on = false;
            }
            if (on && text.charAt(i) != '\n') {
                out.append(amp(hue(tick, index++)));
            }
            out.append(text.charAt(i));
        }
        return out.toString();
    }

    private static int hue(int tick, int index) {
        float h = ((tick * 12 + index * 24) % 360) / 360f;
        return Color.HSBtoRGB(h, 0.9f, 1f) & 0xFFFFFF;
    }

    private static int[] colors(String args) {
        int[] out = {0xFFFFFF, 0x55FFFF};
        if (args == null || args.isBlank()) {
            return out;
        }
        String[] parts = args.split(",");
        if (parts.length > 0) {
            out[0] = parse(parts[0].trim(), out[0]);
        }
        if (parts.length > 1) {
            out[1] = parse(parts[1].trim(), out[1]);
        }
        return out;
    }

    private static int parse(String raw, int fallback) {
        if (raw.length() == 2 && raw.charAt(0) == '&') {
            int at = LEGACY.indexOf(Character.toLowerCase(raw.charAt(1)));
            if (at >= 0) {
                return LEGACY_RGB[at];
            }
        }
        Integer color = Colors.color(raw.startsWith("#") ? raw : "#" + raw);
        return color == null ? fallback : color & 0xFFFFFF;
    }

    // count by code point like Colors, else an emoji eats two hue steps
    private static int visible(String text) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (format(text, i)) {
                i++;
                continue;
            }
            int codePoint = text.codePointAt(i);
            if (codePoint != '\n') {
                count++;
            }
            i += Character.charCount(codePoint) - 1;
        }
        return count;
    }

    private static boolean format(String text, int i) {
        if (text.charAt(i) != '&' || i + 1 >= text.length()) {
            return false;
        }
        char code = Character.toLowerCase(text.charAt(i + 1));
        return Colors.FORMATS.indexOf(code) >= 0;
    }

    private static int earliest(int a, int b) {
        if (a < 0) {
            return b;
        }
        if (b < 0) {
            return a;
        }
        return Math.min(a, b);
    }

    private static int number(String raw) {
        String digits = raw.startsWith(":") ? raw.substring(1) : raw;
        int value = 0;
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            value = value * 10 + (c - '0');
        }
        return value;
    }

    private static String amp(int rgb) {
        return String.format("<#%06X>", rgb & 0xFFFFFF);
    }

    private interface Hue {
        int rgb(int tick, int index);
    }
}
