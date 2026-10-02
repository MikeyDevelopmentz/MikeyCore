package mikey.me.core.text;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaceholdersTest {

    @Test
    void findsARealToken() {
        assertTrue(Placeholders.contains("hi %player_name%"));
        assertTrue(Placeholders.any(List.of("plain", "%staff_online%")));
        assertFalse(Placeholders.contains("100%"));
        assertFalse(Placeholders.contains("%%"));
        assertFalse(Placeholders.contains("% %"));
        assertFalse(Placeholders.contains(null));
        assertFalse(Placeholders.any(List.of("nope")));
        assertFalse(Placeholders.any(null));
    }
}
