package mikey.me.core.web;

import mikey.me.core.persistence.YamlFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

public final class WebApps {

    private static final Object LOCK = new Object();
    private static final Object TOKENS = new Object();
    private static final ConcurrentHashMap<String, Consumer<WebServer>> APPS = new ConcurrentHashMap<>();
    private static final List<Pass> PASSES = new ArrayList<>();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;
    private static final int DEFAULT_HOURS = 12;
    private static volatile WebServer server;
    private static volatile int hours = DEFAULT_HOURS;
    // web.yml luckperms. off means offline editor tokens stay on op
    private static volatile boolean luckperms;
    // null rejects every player token. holograms sets this to the live permission check
    private static volatile Predicate<String> checker;

    private WebApps() {
    }

    // plugins/MikeyCore/web.yml. off until enabled, empty token stays on localhost
    public static WebServer open(Path file) throws IOException {
        if (file == null) {
            throw new IOException("path is null");
        }
        // restart drops every player token, even if token-hours is left
        clearPasses();
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent() == null ? Path.of(".") : file.getParent());
            Files.writeString(file, """
                    # shared http server. empty token stays on localhost
                    enabled: false
                    port: 8080
                    token: ''
                    token-hours: 12
                    # offline editor tokens ask luckperms instead of op
                    luckperms: false
                    """);
        }
        Map<String, Object> map = YamlFile.read(file);
        int span = YamlFile.integer(map, "token-hours", DEFAULT_HOURS);
        hours = span < 1 ? DEFAULT_HOURS : span;
        luckperms = YamlFile.bool(map, "luckperms", false);
        if (!YamlFile.bool(map, "enabled", false)) {
            return null;
        }
        int port = YamlFile.integer(map, "port", 8080);
        // blank stays localhost with no auth. a file token only means bind public, it is not a password
        boolean local = YamlFile.string(map, "token").trim().isEmpty();
        return launch(port, "", local);
    }

    // one more token for this player. dies on restart, or when token-hours is up
    public static String issue(String player) {
        int span = hours < 1 ? DEFAULT_HOURS : hours;
        Instant until = Instant.now().plus(span, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        return issue(player, until);
    }

    static String issue(String player, Instant expires) {
        if (player == null || player.isBlank() || server == null) {
            return null;
        }
        byte[] raw = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(raw);
        String fresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        synchronized (TOKENS) {
            Instant now = Instant.now();
            PASSES.removeIf(pass -> pass.expires != null && !pass.expires.isAfter(now));
            PASSES.add(new Pass(fresh.getBytes(StandardCharsets.UTF_8), player.trim(), expires));
        }
        return fresh;
    }

    public static void allow(Predicate<String> check) {
        checker = check;
    }

    public static boolean luckperms() {
        return luckperms;
    }

    // true if got matches any live player token and that player still passes the check
    static boolean accept(byte[] got) {
        if (got == null || got.length == 0) {
            return false;
        }
        String player = null;
        Instant expires = null;
        synchronized (TOKENS) {
            for (Pass pass : PASSES) {
                // still compare the rest, so a hit doesnt skip the other tokens
                boolean same = MessageDigest.isEqual(pass.token, got);
                if (same && player == null) {
                    player = pass.player;
                    expires = pass.expires;
                }
            }
        }
        if (player == null) {
            return false;
        }
        if (expires != null && !expires.isAfter(Instant.now())) {
            return false;
        }
        Predicate<String> check = checker;
        if (check == null) {
            return false;
        }
        try {
            return check.test(player);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static WebServer start(int port, String token) throws IOException {
        clearPasses();
        String secret = token == null ? "" : token.trim();
        return launch(port, secret, secret.isEmpty());
    }

    private static WebServer launch(int port, String token, boolean local) throws IOException {
        WebServer started = WebServer.listen(port, token, local);
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

    private static void clearPasses() {
        synchronized (TOKENS) {
            PASSES.clear();
        }
    }

    static void reset() {
        stop();
        APPS.clear();
        clearPasses();
        hours = DEFAULT_HOURS;
        luckperms = false;
        checker = null;
    }

    private record Pass(byte[] token, String player, Instant expires) {
    }
}
