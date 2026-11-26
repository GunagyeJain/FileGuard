package com.fileguard.client;

import com.fileguard.model.LogEntry;
import com.fileguard.model.LogLevel;
import com.fileguard.server.BackendServer;
import com.fileguard.server.Json;
import com.fileguard.service.ActivityLogger;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Talks to the FileGuard backend over HTTP (java.net.http, built into Java).
 *
 * Sending a log never blocks the UI: requests are sent asynchronously. If the
 * backend is offline, logs wait in a small queue and are sent once it is back.
 * A background check every few seconds keeps the online/offline status current.
 */
public class BackendClient implements ActivityLogger, AutoCloseable {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);
    private static final int MAX_QUEUE = 500;
    private static final long HEALTH_INTERVAL_SECONDS = 5;

    private final URI baseUri;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(TIMEOUT).build();
    private final Queue<String> pending = new ConcurrentLinkedQueue<>();
    private final List<Consumer<Boolean>> statusListeners = new CopyOnWriteArrayList<>();
    private ScheduledExecutorService monitor;
    private volatile boolean online;

    public BackendClient(URI baseUri) {
        this.baseUri = baseUri;
    }

    public static BackendClient forPort(int port) {
        return new BackendClient(URI.create("http://127.0.0.1:" + port));
    }

    public URI baseUri() {
        return baseUri;
    }

    public boolean isOnline() {
        return online;
    }

    /** Called with true/false whenever the backend goes online or offline (on a background thread). */
    public void addStatusListener(Consumer<Boolean> listener) {
        statusListeners.add(listener);
    }

    /** Starts the periodic health check. */
    public void startMonitoring() {
        monitor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "fileguard-backend-monitor");
            t.setDaemon(true);
            return t;
        });
        monitor.scheduleWithFixedDelay(() -> {
            if (checkHealth()) {
                flushPending();
            }
        }, 0, HEALTH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        if (monitor != null) {
            monitor.shutdownNow();
        }
    }

    // -------------------------------------------------------------- API calls

    /** @return true if a FileGuard backend answers at the base address */
    public boolean checkHealth() {
        try {
            Map<String, Object> data = asMap(call(HttpRequest.newBuilder(uri("/api/health")).GET()));
            boolean ok = BackendServer.SERVICE_NAME.equals(data.get("service"));
            setOnline(ok);
            return ok;
        } catch (Exception e) {
            setOnline(false);
            return false;
        }
    }

    /** Sends a log asynchronously. Never throws; queues the log if the backend is offline. */
    @Override
    public void log(LogLevel level, String category, String message, String details) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("level", level.name());
        body.put("category", category);
        body.put("message", truncate(message, 500));
        body.put("details", truncate(details == null ? "" : details, 4000));
        String json = Json.write(body);
        if (!online) {
            enqueue(json);
            return;
        }
        http.sendAsync(post(json), HttpResponse.BodyHandlers.ofString())
                .whenComplete((response, error) -> {
                    if (error != null || response.statusCode() >= 500) {
                        setOnline(false);
                        enqueue(json);
                    }
                });
    }

    /** Loads logs (newest first). Blocking: call from a background thread. */
    public List<LogEntry> fetchLogs(int limit, LogLevel level) throws IOException, InterruptedException {
        String path = "/api/logs?limit=" + limit
                + (level == null ? "" : "&level=" + URLEncoder.encode(level.name(), StandardCharsets.UTF_8));
        Object data = call(HttpRequest.newBuilder(uri(path)).GET());
        List<LogEntry> logs = new ArrayList<>();
        if (data instanceof List<?> list) {
            for (Object o : list) {
                logs.add(toEntry(asMap(o)));
            }
        }
        return logs;
    }

    /** Number of logs per level plus "total". Blocking. */
    public Map<String, Integer> fetchStats() throws IOException, InterruptedException {
        Map<String, Integer> stats = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : asMap(call(HttpRequest.newBuilder(uri("/api/stats")).GET())).entrySet()) {
            stats.put(e.getKey(), ((Number) e.getValue()).intValue());
        }
        return stats;
    }

    /** Deletes all logs on the backend. Blocking. */
    public int clearLogs() throws IOException, InterruptedException {
        Object deleted = asMap(call(HttpRequest.newBuilder(uri("/api/logs")).DELETE())).get("deleted");
        return deleted instanceof Number n ? n.intValue() : 0;
    }

    // ---------------------------------------------------------------- helpers

    private Object call(HttpRequest.Builder builder) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(builder.timeout(TIMEOUT).build(), HttpResponse.BodyHandlers.ofString());
        Map<String, Object> envelope;
        try {
            envelope = Json.parseObject(response.body());
        } catch (IllegalArgumentException e) {
            throw new IOException("Backend sent an invalid response (HTTP " + response.statusCode() + ")");
        }
        if (!Boolean.TRUE.equals(envelope.get("success"))) {
            throw new IOException("Backend error: " + envelope.get("error"));
        }
        setOnline(true);
        return envelope.get("data");
    }

    private void flushPending() {
        String json;
        while (online && (json = pending.poll()) != null) {
            try {
                HttpResponse<String> r = http.send(post(json), HttpResponse.BodyHandlers.ofString());
                if (r.statusCode() >= 500) {
                    pending.add(json);
                    return;
                }
            } catch (IOException e) {
                pending.add(json);
                setOnline(false);
                return;
            } catch (InterruptedException e) {
                pending.add(json);
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void enqueue(String json) {
        if (pending.size() < MAX_QUEUE) {
            pending.add(json);
        }
    }

    private HttpRequest post(String json) {
        return HttpRequest.newBuilder(uri("/api/logs")).timeout(TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json)).build();
    }

    private URI uri(String path) {
        return baseUri.resolve(path);
    }

    private void setOnline(boolean value) {
        boolean changed = online != value;
        online = value;
        if (changed) {
            statusListeners.forEach(l -> l.accept(value));
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) throws IOException {
        if (o instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new IOException("Unexpected response from backend");
    }

    private static LogEntry toEntry(Map<String, Object> m) {
        return new LogEntry(((Number) m.get("id")).longValue(), LocalDateTime.parse((String) m.get("timestamp")),
                LogLevel.valueOf((String) m.get("level")), (String) m.get("category"),
                (String) m.get("message"), (String) m.getOrDefault("details", ""));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** Number of logs waiting to be sent (for tests and the status line). */
    public int pendingCount() {
        return pending.size();
    }
}
