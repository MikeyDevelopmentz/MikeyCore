package mikey.me.core.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YamlFileTest {

    @TempDir
    Path dir;

    @Test
    void roundTripsNestedValues() throws Exception {
        Map<String, Object> spawn = new LinkedHashMap<>();
        spawn.put("world", "world");
        spawn.put("x", 1.5);
        spawn.put("shadow", true);
        spawn.put("lines", List.of("hello", "#BLOCK: stone"));
        Map<String, Object> clicks = new LinkedHashMap<>();
        clicks.put("left", List.of("msg:hi"));
        spawn.put("clicks", clicks);
        Map<String, Object> root = new LinkedHashMap<>();
        Map<String, Object> holograms = new LinkedHashMap<>();
        holograms.put("spawn", spawn);
        root.put("holograms", holograms);

        Path file = dir.resolve("holograms.yml");
        YamlFile.write(file, root);
        Map<String, Object> loaded = YamlFile.read(file);
        Map<String, Object> back = YamlFile.map(YamlFile.map(loaded, "holograms"), "spawn");

        assertEquals("world", YamlFile.string(back, "world"));
        assertEquals(1.5, YamlFile.number(back, "x", 0));
        assertTrue(YamlFile.bool(back, "shadow", false));
        assertEquals(List.of("hello", "#BLOCK: stone"), YamlFile.strings(back, "lines"));
        assertEquals(List.of("msg:hi"), YamlFile.strings(YamlFile.map(back, "clicks"), "left"));
    }

    @Test
    void readsBukkitStyleLists() throws Exception {
        Path file = dir.resolve("old.yml");
        Files.writeString(file, """
                holograms:
                  spawn:
                    world: world
                    y: 80
                    lines:
                    - '&eSpawn'
                    - '#ITEM: diamond'
                    clicks:
                      right:
                      - console:say hi
                """);
        Map<String, Object> spawn = YamlFile.map(YamlFile.map(YamlFile.read(file), "holograms"), "spawn");
        assertEquals(80, YamlFile.integer(spawn, "y", 0));
        assertEquals(List.of("&eSpawn", "#ITEM: diamond"), YamlFile.strings(spawn, "lines"));
        assertEquals(List.of("console:say hi"), YamlFile.strings(YamlFile.map(spawn, "clicks"), "right"));
    }

    @Test
    void missingFileIsEmpty() throws Exception {
        assertTrue(YamlFile.read(dir.resolve("nope.yml")).isEmpty());
    }

    // keys with quotes used to break the reader's key/value split, couldnt read back own output
    @Test
    void roundTripsKeysContainingQuotes() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("a\"b", "v");
        root.put("a'b", "v");
        root.put("a\\b", "v");
        Path file = dir.resolve("quoted.yml");
        YamlFile.write(file, root);
        Map<String, Object> back = YamlFile.read(file);
        assertEquals("v", back.get("a\"b"));
        assertEquals("v", back.get("a'b"));
        assertEquals("v", back.get("a\\b"));
    }

    // empty map read back as "", empty list read back as "[]"
    @Test
    void roundTripsEmptyCollections() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("emptyMap", Map.of());
        root.put("emptyList", List.of());
        Path file = dir.resolve("empty.yml");
        YamlFile.write(file, root);
        Map<String, Object> back = YamlFile.read(file);
        assertTrue(back.get("emptyMap") instanceof Map, "empty map must stay a map");
        assertEquals(Map.of(), back.get("emptyMap"));
        assertTrue(back.get("emptyList") instanceof List, "empty list must stay a list");
        assertEquals(List.of(), back.get("emptyList"));
    }

    // null and "" both wrote as "''" and were indistinguishable
    @Test
    void roundTripsNullDistinctFromEmptyString() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("a", null);
        root.put("b", "");
        Path file = dir.resolve("null.yml");
        YamlFile.write(file, root);
        Map<String, Object> back = YamlFile.read(file);
        assertTrue(back.containsKey("a"));
        assertEquals(null, back.get("a"));
        assertEquals("", back.get("b"));
    }

    // renderScalar only handled Boolean/Integer/Long/Double/Float, so a Short came back as a String
    @Test
    void roundTripsSmallIntegralTypes() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("s", (short) 5);
        root.put("b", (byte) 5);
        root.put("i", 5);
        Path file = dir.resolve("numbers.yml");
        YamlFile.write(file, root);
        Map<String, Object> back = YamlFile.read(file);
        assertEquals(5, back.get("s"));
        assertEquals(5, back.get("b"));
        assertEquals(5, back.get("i"));
    }

    // plain() emitted values a YAML 1.1 reader resolves to a different type or rejects
    @Test
    void quotesScalarsThatYamlWouldMisread() throws Exception {
        Map<String, Object> root = new LinkedHashMap<>();
        for (String risky : List.of("yes", "no", "on", "off", "|foo", ">foo", "~", "0x1F", ".inf", "@x", "`x", "TRUE")) {
            root.put("k" + risky.hashCode() + risky.length(), risky);
        }
        Path file = dir.resolve("risky.yml");
        YamlFile.write(file, root);
        Map<String, Object> back = YamlFile.read(file);
        for (String risky : List.of("yes", "no", "on", "off", "|foo", ">foo", "~", "0x1F", ".inf", "@x", "`x", "TRUE")) {
            String key = "k" + risky.hashCode() + risky.length();
            assertEquals(risky, back.get(key), "must survive as the string it was written as: " + risky);
        }
    }

    // write() used a fixed tmp sibling, concurrent writers spliced payloads into a file that still parsed
    @Test
    void concurrentWritesDoNotSplice() throws Exception {
        Path file = dir.resolve("contended.yml");
        Map<String, Object> a = new LinkedHashMap<>();
        Map<String, Object> b = new LinkedHashMap<>();
        for (int i = 0; i < 300; i++) {
            a.put("a" + i, i);
            b.put("b" + i, i);
        }
        Thread first = new Thread(() -> writeQuietly(file, a));
        Thread second = new Thread(() -> writeQuietly(file, b));
        first.start();
        second.start();
        first.join();
        second.join();

        Map<String, Object> back = YamlFile.read(file);
        boolean allA = back.keySet().stream().allMatch(k -> k.startsWith("a"));
        boolean allB = back.keySet().stream().allMatch(k -> k.startsWith("b"));
        assertTrue(allA || allB, "file must contain one writer's content, not a splice of both");
    }

    private static void writeQuietly(Path file, Map<String, Object> content) {
        try {
            YamlFile.write(file, content);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
