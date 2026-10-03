package mikey.me.core.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class WebServer {

    private final HttpServer server;
    private final ExecutorService executor;
    private final byte[] token;
    private final boolean localOnly;
    private final CopyOnWriteArrayList<Route> routes = new CopyOnWriteArrayList<>();
    private final ThreadLocal<String> routeOwner = new ThreadLocal<>();
    private volatile boolean stopped;

    private WebServer(HttpServer server, ExecutorService executor, byte[] token, boolean localOnly) {
        this.server = server;
        this.executor = executor;
        this.token = token;
        this.localOnly = localOnly;
    }

    // empty token stays on localhost. dont open the editor on every interface with no token
    public static WebServer start(int port, String token) throws IOException {
        String secret = token == null ? "" : token.trim();
        return listen(port, secret, secret.isEmpty());
    }

    // localOnly false and an empty secret is the public bind. player tokens live on WebApps
    static WebServer listen(int port, String token, boolean localOnly) throws IOException {
        if (port < 0 || port > 65535) {
            throw new IOException("bad port " + port);
        }
        String secret = token == null ? "" : token.trim();
        // bytes, not a hostname, so this stays ipv4 and doesnt flip to the ipv6 wildcard
        byte[] addr = localOnly ? new byte[] {127, 0, 0, 1} : new byte[] {0, 0, 0, 0};
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByAddress(addr), port), 0);
        ExecutorService executor = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "mikey-web");
            thread.setDaemon(true);
            return thread;
        });
        WebServer web = new WebServer(server, executor, secret.getBytes(StandardCharsets.UTF_8), localOnly);
        server.createContext("/", web::handle);
        server.setExecutor(executor);
        server.start();
        return web;
    }

    public void route(String method, String path, WebHandler handler) {
        boolean prefix = path.endsWith("*");
        String clean = prefix ? path.substring(0, path.length() - 1) : path;
        String owner = routeOwner.get();
        routes.add(new Route(owner == null ? "" : owner, method.toUpperCase(Locale.ROOT), clean, prefix, handler));
    }

    public void html(String path, String body) {
        String page = body == null ? "" : body;
        route("GET", path, exchange -> exchange.html(200, page));
    }

    // routes added inside here belong to that plugin, so a reload can drop just those
    void bind(String owner, Runnable add) {
        String prev = routeOwner.get();
        routeOwner.set(owner == null ? "" : owner);
        try {
            add.run();
        } finally {
            if (prev == null) {
                routeOwner.remove();
            } else {
                routeOwner.set(prev);
            }
        }
    }

    void drop(String owner) {
        if (owner == null) {
            return;
        }
        routes.removeIf(route -> owner.equals(route.owner));
    }

    public void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        server.stop(0);
        executor.shutdownNow();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String host() {
        return server.getAddress().getAddress().getHostAddress();
    }

    public boolean localOnly() {
        return localOnly;
    }

    private void handle(HttpExchange exchange) {
        WebExchange web = null;
        try {
            String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.isEmpty()) {
                path = "/";
            }
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            if (!allowed(exchange)) {
                web = new WebExchange(exchange, "");
                web.json(401, "{\"error\":\"bad token\"}");
                return;
            }
            Route route = find(method, path);
            if (route == null) {
                web = new WebExchange(exchange, "");
                web.json(404, "{\"error\":\"not found\"}");
                return;
            }
            String tail = route.prefix ? path.substring(route.path.length()) : "";
            web = new WebExchange(exchange, tail);
            route.handler.handle(web);
            if (!web.sent()) {
                web.json(500, "{\"error\":\"broke\"}");
            }
        } catch (IOException e) {
            if (web == null || !web.sent()) {
                WebExchange fail = web == null ? new WebExchange(exchange, "") : web;
                int status = "too big".equals(e.getMessage()) ? 413 : 500;
                fail.json(status, "{\"error\":\"" + (status == 413 ? "too big" : "broke") + "\"}");
            }
        } catch (RuntimeException e) {
            if (web == null || !web.sent()) {
                WebExchange fail = web == null ? new WebExchange(exchange, "") : web;
                fail.json(500, "{\"error\":\"broke\"}");
            }
        } finally {
            exchange.close();
        }
    }

    private boolean allowed(HttpExchange exchange) {
        // blank web.yml token. public bind still needs a player token
        if (localOnly) {
            return true;
        }
        String authorization = exchange.getRequestHeaders().getFirst("Authorization");
        if (authorization != null) {
            String value = authorization;
            if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
                value = value.substring(7).trim();
            } else {
                value = value.trim();
            }
            if (matches(value)) {
                return true;
            }
        }
        String header = exchange.getRequestHeaders().getFirst("X-Token");
        if (header != null && matches(header.trim())) {
            return true;
        }
        String query = new WebExchange(exchange, "").query("token");
        return query != null && matches(query);
    }

    private boolean matches(String got) {
        if (got == null || got.isEmpty()) {
            return false;
        }
        byte[] raw = got.getBytes(StandardCharsets.UTF_8);
        // direct start() secret. player tokens are separate and recheck the player
        if (token.length > 0 && MessageDigest.isEqual(token, raw)) {
            return true;
        }
        return WebApps.accept(raw);
    }

    private Route find(String method, String path) {
        Route prefix = null;
        for (Route route : routes) {
            if (!route.method.equals(method)) {
                continue;
            }
            if (!route.prefix && route.path.equals(path)) {
                return route;
            }
            if (route.prefix && path.startsWith(route.path) && (prefix == null || route.path.length() > prefix.path.length())) {
                prefix = route;
            }
        }
        return prefix;
    }

    private record Route(String owner, String method, String path, boolean prefix, WebHandler handler) {
    }
}
