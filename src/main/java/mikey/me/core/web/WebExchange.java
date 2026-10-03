package mikey.me.core.web;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public final class WebExchange {

    private final HttpExchange exchange;
    private final String tail;
    private boolean sent;

    WebExchange(HttpExchange exchange, String tail) {
        this.exchange = exchange;
        this.tail = tail;
    }

    public String tail() {
        return tail;
    }

    public String header(String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    public String query(String name) {
        String raw = exchange.getRequestURI().getRawQuery();
        if (raw == null || raw.isEmpty() || name == null) {
            return null;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            String key = decode(eq < 0 ? part : part.substring(0, eq));
            if (!name.equals(key)) {
                continue;
            }
            return eq < 0 ? "" : decode(part.substring(eq + 1));
        }
        return null;
    }

    public String body() throws IOException {
        try (InputStream in = exchange.getRequestBody()) {
            byte[] data = in.readNBytes(1_000_001);
            if (data.length > 1_000_000) {
                throw new IOException("too big");
            }
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    public void json(int status, String body) {
        send(status, "application/json", body == null ? "" : body);
    }

    public void html(int status, String body) {
        send(status, "text/html", body == null ? "" : body);
    }

    boolean sent() {
        return sent;
    }

    void send(int status, String type, String body) {
        if (sent) {
            return;
        }
        sent = true;
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        try {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.getResponseBody().close();
        } catch (IOException ignored) {
            // client went away
        }
    }

    private static String decode(String raw) {
        try {
            return URLDecoder.decode(raw, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }
}
