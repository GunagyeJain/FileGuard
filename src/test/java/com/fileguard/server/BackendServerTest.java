package com.fileguard.server;

import com.fileguard.client.BackendClient;
import com.fileguard.core.ProgressListener;
import com.fileguard.db.Database;
import com.fileguard.model.LogEntry;
import com.fileguard.model.LogLevel;
import com.fileguard.service.ScanService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Starts a real backend on a free port and talks to it over HTTP. */
class BackendServerTest {

    @TempDir
    Path tmp;
    private BackendServer server;
    private BackendClient client;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void start() throws Exception {
        server = BackendServer.start(0, tmp.resolve("backend.db"));
        client = BackendClient.forPort(server.port());
    }

    @AfterEach
    void stop() {
        client.close();
        server.stop();
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .header("Content-Type", "application/json");
        b.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthIdentifiesTheService() throws Exception {
        assertTrue(client.checkHealth());
        assertTrue(client.isOnline());
        HttpResponse<String> r = send("GET", "/api/health", null);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("\"service\":\"fileguard-backend\""));
    }

    @Test
    void storesAndListsLogsWithFilters() throws Exception {
        assertEquals(201, send("POST", "/api/logs",
                "{\"level\":\"INFO\",\"category\":\"scan\",\"message\":\"Scan started\",\"details\":\"C:/data\"}").statusCode());
        assertEquals(201, send("POST", "/api/logs",
                "{\"level\":\"ERROR\",\"category\":\"EXPORT\",\"message\":\"Disk full\"}").statusCode());

        List<LogEntry> all = client.fetchLogs(50, null);
        assertEquals(2, all.size());
        assertEquals("Disk full", all.get(0).message(), "newest first");
        assertEquals("SCAN", all.get(1).category(), "category is stored upper-case");
        assertEquals("C:/data", all.get(1).details());

        List<LogEntry> errors = client.fetchLogs(50, LogLevel.ERROR);
        assertEquals(1, errors.size());
        assertEquals(LogLevel.ERROR, errors.get(0).level());

        Map<String, Integer> stats = client.fetchStats();
        assertEquals(2, stats.get("total"));
        assertEquals(1, stats.get("INFO"));
        assertEquals(0, stats.get("WARN"));
    }

    @Test
    void rejectsInvalidRequests() throws Exception {
        assertEquals(400, send("POST", "/api/logs", "{\"level\":\"LOUD\",\"category\":\"A\",\"message\":\"x\"}").statusCode());
        assertEquals(400, send("POST", "/api/logs", "{\"level\":\"INFO\",\"category\":\"A\"}").statusCode());
        assertEquals(400, send("POST", "/api/logs", "{\"level\":\"INFO\",\"category\":\"bad category!\",\"message\":\"x\"}").statusCode());
        assertEquals(400, send("POST", "/api/logs", "not json").statusCode());
        assertEquals(400, send("GET", "/api/logs?limit=abc", null).statusCode());
        assertEquals(405, send("PUT", "/api/health", "{}").statusCode());
        assertEquals(404, send("GET", "/nope", null).statusCode());
        assertEquals(413, send("POST", "/api/logs", "{\"x\":\"" + "a".repeat(20_000) + "\"}").statusCode());
        HttpResponse<String> r = send("POST", "/api/logs", "{}");
        assertTrue(r.body().contains("\"success\":false"), "errors use the standard envelope");
    }

    @Test
    void clearDeletesEverything() throws Exception {
        client.checkHealth();
        send("POST", "/api/logs", "{\"level\":\"WARN\",\"category\":\"APP\",\"message\":\"one\"}");
        assertEquals(1, client.clearLogs());
        assertTrue(client.fetchLogs(10, null).isEmpty());
    }

    @Test
    void servesTheLogViewerPage() throws Exception {
        HttpResponse<String> r = send("GET", "/", null);
        assertEquals(200, r.statusCode());
        assertTrue(r.body().contains("FileGuard Backend"));
    }

    @Test
    void clientQueuesLogsWhileOfflineAndSendsThemLater() throws Exception {
        int port = server.port();
        server.stop();
        BackendClient offline = BackendClient.forPort(port);
        assertFalse(offline.checkHealth());
        offline.info("APP", "queued while offline");
        assertEquals(1, offline.pendingCount());

        server = BackendServer.start(port, tmp.resolve("backend.db"));
        offline.startMonitoring(); // first health check runs immediately and flushes the queue
        long deadline = System.currentTimeMillis() + 5000;
        while (offline.pendingCount() > 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        offline.close();
        assertEquals(0, offline.pendingCount());
        assertEquals("queued while offline", client.fetchLogs(10, null).get(0).message());
    }

    @Test
    void scanServiceReportsScansToTheBackend() throws Exception {
        assertTrue(client.checkHealth());
        Path folder = Files.createDirectories(tmp.resolve("data"));
        Files.writeString(folder.resolve("a.txt"), "hello");
        Database db = new Database(tmp.resolve("app.db"));
        db.initialize();
        new ScanService(db, client).createBaseline(folder, ProgressListener.NONE);

        long deadline = System.currentTimeMillis() + 5000;
        List<LogEntry> logs = List.of();
        while (logs.size() < 2 && System.currentTimeMillis() < deadline) {
            logs = client.fetchLogs(10, null);
            Thread.sleep(50);
        }
        assertEquals(2, logs.size(), "started + completed");
        assertTrue(logs.get(0).message().startsWith("Baseline created completed"));
        assertTrue(logs.get(1).message().startsWith("Baseline created started"));
    }
}
