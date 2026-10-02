package mikey.me.core.text;

import java.util.regex.Pattern;

public final class Keys {

    private static final Pattern SIMPLE = Pattern.compile("[a-z0-9_-]+");

    private Keys() {
    }

    public static boolean simple(String value) {
        return value != null && SIMPLE.matcher(value).matches();
    }
}
