package mikey.me.core.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeysTest {

    @Test
    void simpleIds() {
        assertTrue(Keys.simple("spawn"));
        assertTrue(Keys.simple("a_b-1"));
        assertFalse(Keys.simple("Spawn"));
        assertFalse(Keys.simple("a b"));
        assertFalse(Keys.simple(""));
        assertFalse(Keys.simple(null));
    }
}
