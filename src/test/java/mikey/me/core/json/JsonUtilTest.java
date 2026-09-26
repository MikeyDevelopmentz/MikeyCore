package mikey.me.core.json;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonUtilTest {

    @Test
    void readsAndEscapesCompactValues() {
        String json = "{\"text\":\"a\\\"b\\nc\",\"flag\":true,\"count\":-42}";

        assertEquals("a\"b\nc", JsonUtil.extractString(json, "text"));
        assertTrue(JsonUtil.extractBool(json, "flag", false));
        assertEquals(-42, JsonUtil.extractInt(json, "count", 0));
        assertTrue(JsonUtil.hasKey(json, "count"));
        assertFalse(JsonUtil.hasKey(json, "missing"));
        assertEquals("a\\\"b\\nc", JsonUtil.escape("a\"b\nc"));
    }

    @Test
    void rejectsMalformedNumbersAndObjects() {
        assertEquals(7L, JsonUtil.extractLong("{\"count\":42junk}", "count", 7L));
        assertEquals(7, JsonUtil.extractInt("{\"count\":2147483648}", "count", 7));
        assertTrue(JsonUtil.extractBool("{\"flag\":falsehood}", "flag", true));
    }
}
