package mikey.me.core.text;

import java.util.List;
import java.util.regex.Pattern;

public final class Placeholders {

    // %player_name% counts, %% and "100%" dont
    private static final Pattern TOKEN = Pattern.compile("%[^%\\s]+%");

    private Placeholders() {
    }

    public static boolean contains(String text) {
        return text != null && TOKEN.matcher(text).find();
    }

    public static boolean any(List<String> lines) {
        if (lines == null) {
            return false;
        }
        for (String line : lines) {
            if (contains(line)) {
                return true;
            }
        }
        return false;
    }
}
