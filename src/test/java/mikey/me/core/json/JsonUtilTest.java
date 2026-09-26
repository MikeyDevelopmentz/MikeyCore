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

    @Test
    void readsWhitespaceAroundKeysAndValues() {
        String json = " \n{ \"text\" : \"hello\", \"flag\" : true, \"count\" : -42 }\t";
        assertEquals("hello", JsonUtil.extractString(json, "text"));
        assertTrue(JsonUtil.extractBool(json, "flag", false));
        assertEquals(-42L, JsonUtil.extractLong(json, "count", 0L));
        assertTrue(JsonUtil.hasKey(json, "count"));
    }

    @Test
    void ignoresNestedKeysAndQuotedContent() {
        String json = "{\"nested\":{\"count\":1,\"flag\":false},"
                + "\"array\":[{\"text\":\"wrong\"}],\"quoted\":\"{\\\"count\\\":99}\","
                + "\"count\":2,\"flag\":true,\"text\":\"right\"}";
        assertEquals(2, JsonUtil.extractInt(json, "count", -1));
        assertTrue(JsonUtil.extractBool(json, "flag", false));
        assertEquals("right", JsonUtil.extractString(json, "text"));
        assertFalse(JsonUtil.hasKey("{\"nested\":{\"count\":1}}", "count"));
        assertFalse(JsonUtil.hasKey("[{\"count\":1}]", "count"));
    }

    @Test
    void decodesEveryStringEscape() {
        String json = "{\"text\":\"\\b\\f\\n\\r\\t\\\"\\\\\\/\\u0041\"}";
        assertEquals("\b\f\n\r\t\"\\/A", JsonUtil.extractString(json, "text"));
        assertTrue(JsonUtil.extractBool("{\"\\u0066lag\":true}", "flag", false));
    }

    @Test
    void escapedStringsRoundTrip() {
        for (int c = 0; c < 128; c++) {
            String value = "before" + (char) c + "after";
            assertEquals(value, JsonUtil.extractString("{\"text\":\"" + JsonUtil.escape(value) + "\"}", "text"));
        }
    }

    @Test
    void rejectsBrokenStringsAndWrongValueTypes() {
        for (String json : new String[] {
                "{\"text\":\"unfinished}", "{\"text\":\"\\uZZZZ\"}",
                "{\"text\":\"\\q\"}", "{\"text\":\"a\nb\"}",
                "{\"text\":\"hello\"junk}", "{\"text\":true}"}) {
            assertEquals("", JsonUtil.extractString(json, "text"));
        }
        assertEquals(7L, JsonUtil.extractLong("{\"count\": \"42\"}", "count", 7L));
        assertFalse(JsonUtil.extractBool("{\"flag\": \"true\"}", "flag", false));
        assertFalse(JsonUtil.hasKey(null, "flag"));
    }
}
