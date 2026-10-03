package mikey.me.core.web;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebServerTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void bindsLocalAndStops() throws Exception {
        WebServer server = WebServer.start(0, "");
        try {
            assertEquals("127.0.0.1", server.host());
            assertTrue(server.localOnly());
            server.route("GET", "/ping", exchange -> exchange.json(200, "{\"ok\":true}"));
            int port = server.port();

            HttpResponse<String> ping = send(port, "GET", "/ping", null, null);
            assertEquals(200, ping.statusCode());
            assertEquals("{\"ok\":true}", ping.body());
            assertTrue(ping.headers().firstValue("content-type").orElse("").startsWith("application/json"));

            assertEquals(404, send(port, "GET", "/nope", null, null).statusCode());
            assertEquals(200, send(port, "GET", "/ping/", null, null).statusCode());
        } finally {
            int port = server.port();
            server.stop();
            assertThrows(Exception.class, () -> send(port, "GET", "/ping", null, null));
        }
    }

    @Test
    void tokenIsRequiredOffLocalhost() throws Exception {
        WebServer server = WebServer.start(0, "secret");
        try {
            assertFalse(server.localOnly());
            assertFalse("127.0.0.1".equals(server.host()));
            server.route("GET", "/ping", exchange -> exchange.json(200, "{\"ok\":true}"));
            server.route("PUT", "/ping", exchange -> exchange.json(200, exchange.body()));
            int port = server.port();

            assertEquals(401, send(port, "GET", "/ping", null, null).statusCode());
            assertEquals(401, send(port, "PUT", "/ping", "{\"n\":1}", null).statusCode());
            assertEquals(200, send(port, "GET", "/ping", null, "Authorization").statusCode());
            assertEquals(200, send(port, "GET", "/ping", null, "X-Token").statusCode());
            assertEquals(200, send(port, "GET", "/ping?token=secret", null, null).statusCode());
            assertEquals(401, send(port, "GET", "/ping?token=wrong", null, null).statusCode());
        } finally {
            server.stop();
        }
    }

    @Test
    void rejectsABadPort() {
        assertThrows(IOException.class, () -> WebServer.start(-1, ""));
        assertThrows(IOException.class, () -> WebServer.start(65536, ""));
    }

    private HttpResponse<String> send(int port, String method, String path, String body, String header) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path));
        if ("PUT".equals(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else {
            builder.GET();
        }
        if ("Authorization".equals(header)) {
            builder.header("Authorization", "Bearer secret");
        } else if ("X-Token".equals(header)) {
            builder.header("X-Token", "secret");
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
}
