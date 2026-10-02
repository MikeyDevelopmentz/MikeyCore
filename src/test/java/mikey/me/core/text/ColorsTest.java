package mikey.me.core.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ColorsTest {

    @Test
    void hexFormatsAndNormalCodes() {
        String red = "§x§F§F§0§0§0§0";
        assertEquals(red + "hi", Colors.legacy("&#ff0000hi"));
        assertEquals(red + "hi", Colors.legacy("<#ff0000>hi"));
        assertEquals(red + "hi", Colors.legacy("{#ff0000}hi"));
        assertEquals(red + "hi", Colors.legacy("#ff0000hi"));
        assertEquals(red + "Z", Colors.legacy("&x&F&F&0&0&0&0Z"));
        assertEquals("§c§lno", Colors.legacy("&c&lno"));
        assertEquals("100%", Colors.legacy("100%"));
        assertEquals("%player_name%", Colors.legacy("%player_name%"));
        assertEquals("&#zzzzzz", Colors.legacy("&#zzzzzz"));
    }

    @Test
    void gradientKeepsFormatting() {
        assertEquals("§x§F§F§0§0§0§0a§x§0§0§F§F§0§0b", Colors.legacy("<#ff0000>ab</#00ff00>"));
        assertEquals("§l§x§F§F§0§0§0§0a§x§0§0§F§F§0§0b", Colors.legacy("<#ff0000>&lab</#00ff00>"));
        assertEquals("§x§F§F§0§0§0§0hi", Colors.legacy("<#ff0000>hi"));
    }

    @Test
    void colorInts() {
        assertEquals(0xFFFF0000, Colors.color("#ff0000"));
        assertEquals(0x80FF0000, Colors.color("#80ff0000"));
        assertEquals(0, Colors.color("transparent"));
        assertEquals(0, Colors.color("0"));
        assertNull(Colors.color("nope"));
        assertNull(Colors.color(null));
    }

    @Test
    void gradientsKeepWholeCodePoints() {
        String emoji = new String(Character.toChars(0x1F600));
        assertEquals("§x§F§F§0§0§0§0" + emoji, Colors.legacy("<#ff0000>" + emoji + "</#00ff00>"));
        assertEquals("§x§F§F§0§0§0§0" + emoji + "§x§0§0§F§F§0§0b",
                Colors.legacy("<#ff0000>" + emoji + "b</#00ff00>"));
        assertEquals("§x§F§F§0§0§0§0a\n§x§0§0§F§F§0§0" + emoji,
                Colors.legacy("<#ff0000>a\n" + emoji + "</#00ff00>"));
    }
}
