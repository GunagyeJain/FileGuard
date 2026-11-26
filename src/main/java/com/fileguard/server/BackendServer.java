package com.fileguard.server;

import com.fileguard.model.LogEntry;
import com.fileguard.model.LogLevel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * FileGuard's local backend: a small REST server that stores activity logs.
 *
 * It uses the HTTP server that ships with the JDK (package com.sun.net.httpserver),
 * so no web framework is needed. It only listens on 127.0.0.1, so other computers
 * on the network cannot reach it.
 *
 * Endpoints (all responses use {"success": .., "data": .., "error": ..}):
 *   GET    /api/health                       is the backend running?
 *   GET    /api/logs?limit=&level=&category= list logs, newest first
 *   POST   /api/logs                         add a log {level, category, message, details}
 *   DELETE /api/logs                         delete all logs
 *   GET    /api/stats                        number of logs per level
 *   GET    /                                 log viewer web page
 *
 * Run on its own with: java -cp ... com.fileguard.server.BackendServer [port]
 * The desktop app also starts it automatically when it is not already running.
 */
public class BackendServer {

    public static final int DEFAULT_PORT = 8765;
    public static final String SERVICE_NAME = "fileguard-backend";
    public static final String VERSION = com.fileguard.AppInfo.VERSION;

    /** Field limits, also used by the client so both sides agree. */
    public static final int MAX_MESSAGE = 500;
    public static final int MAX_DETAILS = 4000;
    public static final int MAX_CATEGORY = 32;

    private static final int MAX_LEVEL_LENGTH = 10;
    private static final int MAX_BODY_BYTES = 16 * 1024;
    private static final long DRAIN_LIMIT_BYTES = 1024 * 1024;
    private static final int WORKER_THREADS = 4;
    private static final int DEFAULT_LIMIT = 200;
    private static final Pattern CATEGORY = Pattern.compile("[A-Z][A-Z0-9_]{0," + (MAX_CATEGORY - 1) + "}");

    private final HttpServer server;
    private final ExecutorService executor;
    private final LogRepository logs;
    private final long startedAt = System.currentTimeMillis();

    private BackendServer(HttpServer server, ExecutorService executor, LogRepository logs) {
        this.server = server;
        this.executor = executor;
        this.logs = logs;
    }

    /**
     * Starts the server. Use port 0 to let the system choose a free port (used by tests).
     *
     * @throws IOException if the port is already in use
     */
    public static BackendServer start(int port, Path dbFile) throws IOException, SQLException {
        LogRepository repository = new LogRepository(dbFile);
        HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        ExecutorService pool = Executors.newFixedThreadPool(WORKER_THREADS, r -> {
            Thread t = new Thread(r, "fileguard-backend");
            t.setDaemon(true); // never keeps the desktop app alive on exit
            return t;
        });
        BackendServer backend = new BackendServer(http, pool, repository);
        http.createContext("/api/health", backend.safe(backend::health));
        http.createContext("/api/logs", backend.safe(backend::logsEndpoint));
        http.createContext("/api/stats", backend.safe(backend::stats));
        http.createContext("/", backend.safe(backend::webPage));
        http.setExecutor(pool);
        http.start();
        return backend;
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public void stop() {
        server.stop(0);
        executor.shutdownNow();
    }

    /** Default database location: ~/.fileguard/backend.db (override with -Dfileguard.backend.db). */
    public static Path defaultDbFile() {
        String override = System.getProperty("fileguard.backend.db");
        return override != null ? Path.of(override)
                : Path.of(System.getProperty("user.home"), ".fileguard", "backend.db");
    }

    // ------------------------------------------------------------- endpoints

    private void health(HttpExchange ex) throws IOException {
        requirePath(ex, "/api/health");
        requireMethod(ex, "GET");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("service", SERVICE_NAME);
        data.put("version", VERSION);
        data.put("uptimeSeconds", (System.currentTimeMillis() - startedAt) / 1000);
        sendData(ex, 200, data);
    }

    private void logsEndpoint(HttpExchange ex) throws IOException, SQLException {
        requirePath(ex, "/api/logs");
        switch (ex.getRequestMethod()) {
            case "GET" -> {
                Map<String, String> q = query(ex);
                int limit = parseInt(q.get("limit"), DEFAULT_LIMIT);
                LogLevel level = q.containsKey("level") && !q.get("level").isBlank() ? parseLevel(q.get("level")) : null;
                String category = q.containsKey("category") && !q.get("category").isBlank()
                        ? q.get("category").toUpperCase(Locale.ROOT) : null;
                List<Object> list = new ArrayList<>();
                for (LogEntry e : logs.find(limit, level, category)) {
                    list.add(toMap(e));
                }
                sendData(ex, 200, list);
            }
            case "POST" -> {
                requireJson(ex);
                Map<String, Object> body = parseBody(readBody(ex));
                LogLevel level = parseLevel(requireText(body, "level", MAX_LEVEL_LENGTH));
                String category = requireText(body, "category", MAX_CATEGORY).toUpperCase(Locale.ROOT);
                if (!CATEGORY.matcher(category).matches()) {
                    throw new BadRequest("category must be letters, digits or '_' (max " + MAX_CATEGORY + ")");
                }
                String message = requireText(body, "message", MAX_MESSAGE);
                Object details = body.get("details");
                String detailText = details == null ? "" : details.toString();
                if (detailText.length() > MAX_DETAILS) {
                    throw new BadRequest("details is too long (max " + MAX_DETAILS + ")");
                }
                sendData(ex, 201, toMap(logs.insert(level, category, message, detailText)));
            }
            case "DELETE" -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("deleted", logs.clear());
                sendData(ex, 200, data);
            }
            default -> throw new MethodNotAllowed();
        }
    }

    private void stats(HttpExchange ex) throws IOException, SQLException {
        requirePath(ex, "/api/stats");
        requireMethod(ex, "GET");
        Map<String, Object> data = new LinkedHashMap<>();
        int total = 0;
        for (Map.Entry<LogLevel, Integer> e : logs.countByLevel().entrySet()) {
            data.put(e.getKey().name(), e.getValue());
            total += e.getValue();
        }
        data.put("total", total);
        sendData(ex, 200, data);
    }

    private void webPage(HttpExchange ex) throws IOException {
        requireMethod(ex, "GET");
        String path = ex.getRequestURI().getPath();
        if (!path.equals("/") && !path.equals("/index.html")) {
            sendError(ex, 404, "Not found");
            return;
        }
        try (InputStream in = BackendServer.class.getResourceAsStream("/com/fileguard/server/index.html")) {
            if (in == null) {
                sendError(ex, 500, "Viewer page missing");
                return;
            }
            send(ex, 200, "text/html; charset=utf-8", in.readAllBytes());
        }
    }

    // --------------------------------------------------------------- helpers

    @FunctionalInterface
    private interface Endpoint {
        void handle(HttpExchange ex) throws Exception;
    }

    /** Wraps an endpoint so every error becomes a proper JSON error response. */
    private HttpHandler safe(Endpoint endpoint) {
        return ex -> {
            // Not try-with-resources: that would close the exchange *before* the catch
            // blocks run, and the error response could no longer be sent.
            try {
                endpoint.handle(ex);
            } catch (BadRequest e) {
                sendError(ex, 400, e.getMessage());
            } catch (NotFound e) {
                sendError(ex, 404, "Not found");
            } catch (MethodNotAllowed e) {
                sendError(ex, 405, "Method not allowed");
            } catch (UnsupportedMediaType e) {
                sendError(ex, 415, "Content-Type must be application/json");
            } catch (BodyTooLarge e) {
                sendError(ex, 413, "Request body too large");
            } catch (Exception e) {
                // Log details on the server only; the client gets a generic message.
                System.err.println("[fileguard-backend] " + ex.getRequestMethod() + " "
                        + ex.getRequestURI() + " failed: " + e);
                sendError(ex, 500, "Internal server error");
            } finally {
                ex.close();
            }
        };
    }

    static Map<String, Object> toMap(LogEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id());
        m.put("timestamp", e.timestamp().toString());
        m.put("level", e.level().name());
        m.put("category", e.category());
        m.put("message", e.message());
        m.put("details", e.details() == null ? "" : e.details());
        return m;
    }

    /** createContext matches by prefix, so paths like /api/logsXYZ are rejected explicitly. */
    private static void requirePath(HttpExchange ex, String path) {
        String actual = ex.getRequestURI().getPath();
        if (!actual.equals(path) && !actual.equals(path + "/")) {
            throw new NotFound();
        }
    }

    /**
     * Requiring JSON makes browsers send a CORS "preflight" first, which this server does not
     * approve, so a web page open in the browser cannot quietly post fake logs to it.
     */
    private static void requireJson(HttpExchange ex) {
        String type = ex.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            throw new UnsupportedMediaType();
        }
    }

    private static Map<String, Object> parseBody(String text) {
        try {
            return Json.parseObject(text);
        } catch (IllegalArgumentException e) {
            throw new BadRequest("Invalid JSON: " + e.getMessage());
        }
    }

    private static void requireMethod(HttpExchange ex, String method) {
        if (!method.equals(ex.getRequestMethod())) {
            throw new MethodNotAllowed();
        }
    }

    private static String requireText(Map<String, Object> body, String key, int maxLength) {
        Object v = body.get(key);
        if (!(v instanceof String s) || s.isBlank()) {
            throw new BadRequest("'" + key + "' is required");
        }
        if (s.length() > maxLength) {
            throw new BadRequest("'" + key + "' is too long (max " + maxLength + ")");
        }
        return s.trim();
    }

    private static LogLevel parseLevel(String text) {
        try {
            return LogLevel.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequest("level must be INFO, WARN or ERROR");
        }
    }

    private static int parseInt(String text, int fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new BadRequest("limit must be a number");
        }
    }

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> map = new HashMap<>();
        String raw = ex.getRequestURI().getRawQuery();
        if (raw == null) {
            return map;
        }
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String key = URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8);
            String value = eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
            map.put(key, value);
        }
        return map;
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            byte[] data = in.readNBytes(MAX_BODY_BYTES + 1);
            if (data.length > MAX_BODY_BYTES) {
                // Read (and discard) the rest so the client receives our 413 reply instead of a
                // reset connection. Bounded, so a huge upload cannot keep the thread busy.
                byte[] discard = new byte[8192];
                long drained = 0;
                int n;
                while (drained < DRAIN_LIMIT_BYTES && (n = in.read(discard)) != -1) {
                    drained += n;
                }
                throw new BodyTooLarge();
            }
            return new String(data, StandardCharsets.UTF_8);
        }
    }

    private static void sendData(HttpExchange ex, int status, Object data) throws IOException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("success", true);
        envelope.put("data", data);
        envelope.put("error", null);
        send(ex, status, "application/json; charset=utf-8", Json.write(envelope).getBytes(StandardCharsets.UTF_8));
    }

    private static void sendError(HttpExchange ex, int status, String message) throws IOException {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("success", false);
        envelope.put("data", null);
        envelope.put("error", message);
        send(ex, status, "application/json; charset=utf-8", Json.write(envelope).getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }

    private static final class BadRequest extends RuntimeException {
        BadRequest(String message) {
            super(message);
        }
    }

    private static final class MethodNotAllowed extends RuntimeException {
    }

    private static final class NotFound extends RuntimeException {
    }

    private static final class UnsupportedMediaType extends RuntimeException {
    }

    private static final class BodyTooLarge extends RuntimeException {
    }

    // ------------------------------------------------------------ standalone

    /** Runs the backend as its own process (used by "FileGuard Backend.exe"). */
    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Integer.getInteger("fileguard.backend.port", DEFAULT_PORT);
        Path db = defaultDbFile();
        BackendServer backend;
        try {
            backend = start(port, db);
        } catch (IOException e) {
            System.err.println("Could not start the FileGuard backend on port " + port + ": " + e.getMessage());
            System.err.println("Another FileGuard backend (for example the one inside the FileGuard app) may already be running.");
            System.exit(1);
            return;
        }
        System.out.println("FileGuard backend " + VERSION + " is running.");
        System.out.println("  Log viewer : http://127.0.0.1:" + backend.port() + "/");
        System.out.println("  REST API   : http://127.0.0.1:" + backend.port() + "/api/logs");
        System.out.println("  Database   : " + db.toAbsolutePath());
        System.out.println("Close this window (or press Ctrl+C) to stop it.");
        Runtime.getRuntime().addShutdownHook(new Thread(backend::stop));
        Thread.currentThread().join(); // keep running until the process is stopped
    }
}
