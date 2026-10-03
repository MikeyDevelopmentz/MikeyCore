package mikey.me.core.web;

import mikey.me.core.persistence.YamlFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public final class WebApps {

    private static final Object LOCK = new Object();
    private static final ConcurrentHashMap<String, Consumer<WebServer>> APPS = new ConcurrentHashMap<>();
    private static volatile WebServer server;

    private WebApps() {
    }

    // plugins/MikeyCore/web.yml. off until enabled, empty token stays on localhost
    public static WebServer open(Path file) throws IOException {
        if (file == null) {
            throw new IOException("path is null");
        }
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent() == null ? Path.of(".") : file.getParent());
            Files.writeString(file, """
                    # shared http server. empty token stays on localhost
                    enabled: false
                    port: 8080
                    token: ''
                    """);
        }
        Map<String, Object> map = YamlFile.read(file);
        if (!YamlFile.bool(map, "enabled", false)) {
            return null;
        }
        int port = YamlFile.integer(map, "port", 8080);
        return start(port, YamlFile.string(map, "token"));
    }

    public static WebServer start(int port, String token) throws IOException {
        WebServer started = WebServer.start(port, token);
        WebServer old;
        synchronized (LOCK) {
            old = server;
            server = started;
            for (Map.Entry<String, Consumer<WebServer>> app : APPS.entrySet()) {
                started.bind(app.getKey(), () -> app.getValue().accept(started));
            }
        }
        if (old != null) {
            old.stop();
        }
        return started;
    }

    public static void stop() {
        WebServer old;
        synchronized (LOCK) {
            old = server;
            server = null;
        }
        if (old != null) {
            old.stop();
        }
    }

    public static WebServer server() {
        return server;
    }

    // same id replaces that plugin's routes. paths are from the site root
    // dont call register from inside setup
    public static void register(String id, Consumer<WebServer> setup) {
        if (id == null || !id.matches("[a-z0-9_-]+")) {
            throw new IllegalArgumentException("bad id");
        }
        if (setup == null) {
            throw new IllegalArgumentException("setup");
        }
        synchronized (LOCK) {
            APPS.put(id, setup);
            if (server != null) {
                server.drop(id);
                server.bind(id, () -> setup.accept(server));
            }
        }
    }

    public static void unregister(String id) {
        if (id == null) {
            return;
        }
        WebServer current;
        synchronized (LOCK) {
            APPS.remove(id);
            current = server;
        }
        if (current != null) {
            current.drop(id);
        }
    }

    static void reset() {
        stop();
        APPS.clear();
    }
}
