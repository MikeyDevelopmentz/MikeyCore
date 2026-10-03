package mikey.me.core.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            assertEquals("{\"n\":1}", send(port, "/one").body());
            assertEquals("<p>one</p>", send(port, "/one/page").body());
            assertEquals("{\"n\":2}", send(port, "/two").body());

            WebApps.register("one", current -> current.route("GET", "/one", exchange -> exchange.json(200, "{\"n\":9}")));
            assertEquals("{\"n\":9}", send(port, "/one").body());
            assertEquals(404, send(port, "/one/page").statusCode());
            assertEquals("{\"n\":2}", send(port, "/two").body());

            WebApps.unregister("two");
            assertEquals(404, send(port, "/two").statusCode());
            assertEquals(200, send(port, "/one").statusCode());
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
            assertEquals(200, send(server.port(), "/later").statusCode());
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
        assertTrue(Files.readString(file).contains("enabled: false"));

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
    void rejectsABadId() {
        assertThrows(IllegalArgumentException.class, () -> WebApps.register("Holo", server -> {}));
        assertThrows(IllegalArgumentException.class, () -> WebApps.register(null, server -> {}));
    }

    private HttpResponse<String> send(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
