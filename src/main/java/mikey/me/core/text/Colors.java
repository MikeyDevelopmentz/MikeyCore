package mikey.me.core.text;

public final class Colors {

    // minecraft format codes, package-private so Animations can share them and they dont drift
    static final String FORMATS = "klmnor";
    private static final String CODES = "0123456789abcdef" + FORMATS;

    private Colors() {
    }

    // &#rrggbb <#rrggbb> {#rrggbb} #rrggbb &x&r&r&g&g&b&b and <#start>text</#end>
    public static String legacy(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        for (int i = 0; i < input.length();) {
            i = append(input, i, out);
        }
        return out.toString();
    }

    // #rrggbb is opaque, #aarrggbb keeps the alpha. null if its not a color
    public static Integer color(String raw) {
        if (raw == null) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        if (text.equalsIgnoreCase("transparent")) {
            return 0;
        }
        if (text.charAt(0) == '#') {
            String hex = text.substring(1);
            if ((hex.length() == 6 || hex.length() == 8) && allHex(hex)) {
                long value = Long.parseLong(hex, 16);
                if (hex.length() == 6) {
                    value |= 0xFF000000L;
                }
                return (int) value;
            }
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int append(String input, int i, StringBuilder out) {
        int next = tryGradient(input, i, out);
        if (next != i) {
            return next;
        }
        next = tryBungeeHex(input, i, out);
        if (next != i) {
            return next;
        }
        next = tryHex(input, i, out);
        if (next != i) {
            return next;
        }
        if (input.charAt(i) == '&' && i + 1 < input.length() && isCode(input.charAt(i + 1))) {
            out.append('§').append(Character.toLowerCase(input.charAt(i + 1)));
            return i + 2;
        }
        out.append(input.charAt(i));
        return i + 1;
    }

    private static int tryGradient(String input, int i, StringBuilder out) {
        if (!openTag(input, i)) {
            return i;
        }
        int close = findClose(input, i + 9);
        if (close < 0) {
            return i;
        }
        out.append(paint(input.substring(i + 9, close), rgbAt(input, i + 2), rgbAt(input, close + 3)));
        return close + 10;
    }

    // &x&r&r&g&g&b&b
    private static int tryBungeeHex(String input, int i, StringBuilder out) {
        if (i + 14 > input.length() || input.charAt(i) != '&' || Character.toLowerCase(input.charAt(i + 1)) != 'x') {
            return i;
        }
        int rgb = 0;
        for (int n = 0; n < 6; n++) {
            int at = i + 2 + n * 2;
            int digit = Character.digit(input.charAt(at + 1), 16);
            if (input.charAt(at) != '&' || digit < 0) {
                return i;
            }
            rgb = (rgb << 4) | digit;
        }
        out.append(sectionHex(rgb));
        return i + 14;
    }

    private static int tryHex(String input, int i, StringBuilder out) {
        int rgb;
        int end;
        if (starts(input, i, "&#") && hexAt(input, i + 2)) {
            rgb = rgbAt(input, i + 2);
            end = i + 8;
        } else if (openTag(input, i)) {
            rgb = rgbAt(input, i + 2);
            end = i + 9;
        } else if (starts(input, i, "{#") && hexAt(input, i + 2) && i + 8 < input.length() && input.charAt(i + 8) == '}') {
            rgb = rgbAt(input, i + 2);
            end = i + 9;
        } else if (input.charAt(i) == '#' && hexAt(input, i + 1)) {
            rgb = rgbAt(input, i + 1);
            end = i + 7;
        } else {
            return i;
        }
        out.append(sectionHex(rgb));
        return end;
    }

    private static String paint(String inner, int start, int end) {
        int visible = 0;
        for (int i = 0; i < inner.length(); i++) {
            if (isFormatPair(inner, i)) {
                i++;
                continue;
            }
            int codePoint = inner.codePointAt(i);
            if (codePoint != '\n') {
                visible++;
            }
            i += Character.charCount(codePoint) - 1;
        }
        StringBuilder out = new StringBuilder(inner.length() * 2);
        int index = 0;
        int last = Math.max(0, visible - 1);
        for (int i = 0; i < inner.length(); i++) {
            if (isFormatPair(inner, i)) {
                out.append('§').append(Character.toLowerCase(inner.charAt(i + 1)));
                i++;
                continue;
            }
            int c = inner.codePointAt(i);
            if (c == '\n') {
                out.append('\n');
                continue;
            }
            out.append(sectionHex(lerp(start, end, index, last))).appendCodePoint(c);
            i += Character.charCount(c) - 1;
            index++;
        }
        return out.toString();
    }

    private static boolean isFormatPair(String input, int i) {
        return input.charAt(i) == '&' && i + 1 < input.length() && FORMATS.indexOf(Character.toLowerCase(input.charAt(i + 1))) >= 0;
    }

    private static int lerp(int start, int end, int index, int last) {
        if (last <= 0) {
            return start;
        }
        double t = index / (double) last;
        int r = mix((start >> 16) & 0xFF, (end >> 16) & 0xFF, t);
        int g = mix((start >> 8) & 0xFF, (end >> 8) & 0xFF, t);
        int b = mix(start & 0xFF, end & 0xFF, t);
        return (r << 16) | (g << 8) | b;
    }

    private static int mix(int from, int to, double t) {
        return (int) Math.round(from + (to - from) * t);
    }

    private static int findClose(String input, int from) {
        for (int i = from; i + 10 <= input.length(); i++) {
            if (input.charAt(i) == '<' && input.charAt(i + 1) == '/' && input.charAt(i + 2) == '#'
                    && hexAt(input, i + 3) && input.charAt(i + 9) == '>') {
                return i;
            }
        }
        return -1;
    }

    private static boolean openTag(String input, int i) {
        return starts(input, i, "<#") && hexAt(input, i + 2) && i + 8 < input.length() && input.charAt(i + 8) == '>';
    }

    private static boolean starts(String input, int i, String prefix) {
        return i + prefix.length() <= input.length() && input.startsWith(prefix, i);
    }

    private static boolean allHex(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.digit(text.charAt(i), 16) < 0) {
                return false;
            }
        }
        return !text.isEmpty();
    }

    private static boolean hexAt(String input, int i) {
        if (i < 0 || i + 6 > input.length()) {
            return false;
        }
        for (int n = 0; n < 6; n++) {
            if (Character.digit(input.charAt(i + n), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static int rgbAt(String input, int i) {
        return Integer.parseInt(input.substring(i, i + 6), 16);
    }

    private static boolean isCode(char c) {
        return CODES.indexOf(Character.toLowerCase(c)) >= 0;
    }

    private static String sectionHex(int rgb) {
        String hex = String.format("%06X", rgb & 0xFFFFFF);
        StringBuilder out = new StringBuilder(14);
        out.append('§').append('x');
        for (int i = 0; i < hex.length(); i++) {
            out.append('§').append(hex.charAt(i));
        }
        return out.toString();
    }
}
