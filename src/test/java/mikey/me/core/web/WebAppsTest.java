package mikey.me.core.web;

import mikey.me.core.persistence.YamlFile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebAppsTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void cleanup() {
        WebApps.reset();
    }

    @Test
    void pluginsShareOneServer() throws Exception {
        WebApps.register("one", server -> {
            server.route("GET", "/one", exchange -> exchange.json(200, "{\"n\":1}"));
            server.html("/one/page", "<p>one</p>");
        });
        WebApps.register("two", server -> server.route("GET", "/two", exchange -> exchange.json(200, "{\"n\":2}")));
        WebServer server = WebApps.start(0, "");
        try {
            int port = server.port();
            assertEquals("{\"n\":1}", send(port, "/one", null).body());
            assertEquals("<p>one</p>", send(port, "/one/page", null).body());
            assertEquals("{\"n\":2}", send(port, "/two", null).body());

            WebApps.register("one", current -> current.route("GET", "/one", exchange -> exchange.json(200, "{\"n\":9}")));
            assertEquals("{\"n\":9}", send(port, "/one", null).body());
            assertEquals(404, send(port, "/one/page", null).statusCode());
            assertEquals("{\"n\":2}", send(port, "/two", null).body());

            WebApps.unregister("two");
            assertEquals(404, send(port, "/two", null).statusCode());
            assertEquals(200, send(port, "/one", null).statusCode());
        } finally {
            WebApps.stop();
        }
    }

    @Test
    void laterStartPicksUpApps() throws Exception {
        WebApps.register("later", server -> server.route("GET", "/later", exchange -> exchange.json(200, "{\"ok\":true}")));
        assertNull(WebApps.server());
        WebServer server = WebApps.start(0, "");
        try {
            assertEquals(200, send(server.port(), "/later", null).statusCode());
        } finally {
            WebApps.stop();
        }
    }

    @Test
    void openReadsWebYml() throws Exception {
        Path dir = Files.createTempDirectory("mikey-web");
        Path file = dir.resolve("web.yml");
        assertNull(WebApps.open(file));
        assertNull(WebApps.server());
        String written = Files.readString(file);
        assertTrue(written.contains("enabled: false"));
        assertTrue(written.contains("token: ''"));
        assertTrue(written.contains("token-hours: 12"));
        assertTrue(written.contains("luckperms: false"));
        assertFalse(WebApps.luckperms());

        Files.writeString(file, "enabled: true\nport: 0\ntoken: ''\n");
        WebServer server = WebApps.open(file);
        try {
            assertTrue(server.localOnly());
            assertEquals("127.0.0.1", server.host());
        } finally {
            WebApps.stop();
        }
    }

    @Test
    void luckpermsReadsWhenPresentAndStaysOffOtherwise() throws Exception {
        Path file = Files.createTempDirectory("mikey-web").resolve("web.yml");
        String on = "enabled: false\nport: 8080\ntoken: ''\nluckperms: true\n";
        Files.writeString(file, on);
        assertNull(WebApps.open(file));
        assertTrue(WebApps.luckperms());
        assertEquals(on, Files.readString(file));

        String off = "enabled: true\nport: 0\ntoken: ''\n";
        Files.writeString(file, off);
        WebServer server = WebApps.open(file);
        try {
            assertFalse(WebApps.luckperms());
            assertEquals(off, Files.readString(file));
        } finally {
            WebApps.stop();
        }
    }

    @Test
    void missingTokenStaysLocalAndDoesNotWriteOne() throws Exception {
        Path file = Files.createTempDirectory("mikey-web").resolve("web.yml");
        String raw = "enabled: true\nport: 0\nextra: 1\n";
        Files.writeString(file, raw);
        List<String> lines = new ArrayList<>();
        WebApps.register("ping", server -> server.route("GET", "/ping", exchange -> exchange.json(200, "{\"ok\":true}")));
        WebServer server = withLog(lines, () -> WebApps.open(file));
        try {
            assertEquals(raw, Files.readString(file));
            assertFalse(Files.readString(file).contains("token-expires"));
            assertTrue(server.localOnly());
            assertEquals(200, send(server.port(), "/ping", null).statusCode());
            assertTrue(lines.isEmpty());
        } finally {
            WebApps.stop();
        }
    }

    @Test
    void publicBindIgnoresTheFileToken() throws Exception {
        Path file = Files.createTempDirectory("mikey-web").resolve("web.yml");
        String raw = "enabled: true\nport: 0\ntoken: filetoken\ntoken-hours: 12\n";
        Files.writeString(file, raw);
        List<String> lines = new ArrayList<>();
        WebApps.allow(name -> "Steve".equals(name));
        WebApps.register("ping", server -> server.route("GET", "/ping", exchange -> exchange.json(200, "{\"ok\":true}")));
        WebServer server = withLog(lines, () -> WebApps.open(file));
        try {
            assertEquals(raw, Files.readString(file));
            assertFalse(server.localOnly());
            assertEquals(401, send(server.port(), "/ping", null).statusCode());
            assertEquals(401, send(server.port(), "/ping", "filetoken").statusCode());
            assertNull(WebApps.issue(" "));
            String token = WebApps.issue("Steve");
            String second = WebApps.issue("Steve");
            String other = WebApps.issue("Alex");
            assertNotEquals(token, second);
            assertTrue(token.length() >= 43, token);
            assertTrue(Base64.getUrlDecoder().decode(token).length >= 32);
            assertEquals(200, send(server.port(), "/ping", token).statusCode());
            assertEquals(200, send(server.port(), "/ping", second).statusCode());
            assertEquals(401, send(server.port(), "/ping?token=" + other, null).statusCode());
            assertEquals(200, send(server.port(), "/ping?token=" + token, null).statusCode());
            assertTrue(lines.isEmpty());
        } finally {
            WebApps.stop();
        }
    }

    @Test
    void restartDropsTokens() throws Exception {
        Path file = Files.createTempDirectory("mikey-web").resolve("web.yml");
        Files.writeString(file, "enabled: true\nport: 0\ntoken: filetoken\n");
        WebApps.allow(name -> true);
        WebApps.register("ping", server -> server.route("GET", "/ping", exchange -> exchange.json(200, "{\"ok\":true}")));
        WebServer server = WebApps.open(file);
        String token = WebApps.issue("Steve");
        assertEquals(200, send(server.port(), "/ping", token).statusCode());
        WebServer again = WebApps.open(file);
        try {
            assertEquals(401, send(again.port(), "/ping", token).statusCode());
            String fresh = WebApps.issue("Steve");
            assertNotEquals(token, fresh);
            assertEquals(200, send(again.port(), "/ping", fresh).statusCode());
            assertEquals("filetoken", YamlFile.string(YamlFile.read(file), "token"));
        } finally {
            WebApps.stop();
        }
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        Path file = Files.createTempDirectory("mikey-web").resolve("web.yml");
        Files.writeString(file, "enabled: true\nport: 0\ntoken: filetoken\ntoken-hours: 3\n");
        int[] checks = {0};
        WebApps.allow(name -> {
            checks[0]++;
            return true;
        });
        WebApps.register("ping", server -> server.route("GET", "/ping", exchange -> exchange.json(200, "{\"ok\":true}")));
        WebServer server = WebApps.open(file);
        try {
            String dead = WebApps.issue("Steve", Instant.parse("2020-01-01T00:00:00Z"));
            assertEquals(401, send(server.port(), "/ping", dead).statusCode());
            assertEquals(0, checks[0]);
            String live = WebApps.issue("Steve");
            assertEquals(200, send(server.port(), "/ping", live).statusCode());
            assertEquals(1, checks[0]);
            WebApps.allow(name -> false);
            assertEquals(401, send(server.port(), "/ping", live).statusCode());
        } finally {
            WebApps.stop();
        }
    }

    @Test
    void leavesADisabledFileAlone() throws Exception {
        Path file = Files.createTempDirectory("mikey-web").resolve("web.yml");
        String raw = "enabled: false\nport: 8080\ntoken: short\n";
        Files.writeString(file, raw);
        assertNull(WebApps.open(file));
        assertEquals(raw, Files.readString(file));
        assertNull(WebApps.issue("Steve"));
    }

    @Test
    void blankTokenStaysLocal() throws Exception {
        Path file = Files.createTempDirectory("mikey-web").resolve("web.yml");
        String raw = "enabled: true\nport: 0\ntoken: ''\n";
        Files.writeString(file, raw);
        List<String> lines = new ArrayList<>();
        WebApps.register("ping", server -> server.route("GET", "/ping", exchange -> exchange.json(200, "{\"ok\":true}")));
        WebServer server = withLog(lines, () -> WebApps.open(file));
        try {
            assertTrue(server.localOnly());
            assertEquals("127.0.0.1", server.host());
            assertEquals(200, send(server.port(), "/ping", null).statusCode());
            assertEquals(raw, Files.readString(file));
            assertFalse(Files.readString(file).contains("token-expires"));
            assertTrue(lines.isEmpty());
        } finally {
            WebApps.stop();
        }
    }

    @Test
    void rejectsABadId() {
        assertThrows(IllegalArgumentException.class, () -> WebApps.register("Holo", server -> {}));
        assertThrows(IllegalArgumentException.class, () -> WebApps.register(null, server -> {}));
    }

    private HttpResponse<String> send(int port, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET();
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private WebServer withLog(List<String> lines, Open call) throws Exception {
        Logger logger = Logger.getLogger("mikey.me.core.web");
        Level old = logger.getLevel();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                lines.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        handler.setLevel(Level.ALL);
        logger.setLevel(Level.ALL);
        logger.addHandler(handler);
        try {
            return call.open();
        } finally {
            logger.removeHandler(handler);
            logger.setLevel(old);
        }
    }

    @FunctionalInterface
    private interface Open {
        WebServer open() throws Exception;
    }
}
