package com.fileguard.server;

import com.fileguard.model.LogEntry;
import com.fileguard.model.LogLevel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Stores activity logs in the backend's own SQLite database (separate from the
 * desktop app's database, the way a real server keeps its own storage).
 */
public class LogRepository {

    public static final int MAX_LIMIT = 1000;

    private final String url;

    public LogRepository(Path dbFile) throws IOException, SQLException {
        Path parent = dbFile.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        this.url = "jdbc:sqlite:" + dbFile.toAbsolutePath();
        try (Connection c = connect(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS logs (
                        id         INTEGER PRIMARY KEY AUTOINCREMENT,
                        created_at TEXT    NOT NULL,
                        level      TEXT    NOT NULL,
                        category   TEXT    NOT NULL,
                        message    TEXT    NOT NULL,
                        details    TEXT
                    )""");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_logs_level ON logs(level)");
        }
    }

    private Connection connect() throws SQLException {
        Properties props = new Properties();
        // Several HTTP requests may write at once; wait instead of failing on a locked database.
        props.setProperty("busy_timeout", "5000");
        return DriverManager.getConnection(url, props);
    }

    public LogEntry insert(LogLevel level, String category, String message, String details) throws SQLException {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO logs (created_at, level, category, message, details) VALUES (?, ?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, now.toString());
            ps.setString(2, level.name());
            ps.setString(3, category);
            ps.setString(4, message);
            ps.setString(5, details);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return new LogEntry(keys.getLong(1), now, level, category, message, details);
            }
        }
    }

    /**
     * Newest first. {@code level} and {@code category} are optional filters (null = all).
     */
    public List<LogEntry> find(int limit, LogLevel level, String category) throws SQLException {
        int safeLimit = Math.max(1, Math.min(MAX_LIMIT, limit));
        String levelName = level == null ? null : level.name();
        List<LogEntry> list = new ArrayList<>();
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT id, created_at, level, category, message, details FROM logs "
                             + "WHERE (? IS NULL OR level = ?) AND (? IS NULL OR category = ?) "
                             + "ORDER BY id DESC LIMIT ?")) {
            ps.setString(1, levelName);
            ps.setString(2, levelName);
            ps.setString(3, category);
            ps.setString(4, category);
            ps.setInt(5, safeLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new LogEntry(rs.getLong("id"), LocalDateTime.parse(rs.getString("created_at")),
                            LogLevel.valueOf(rs.getString("level")), rs.getString("category"),
                            rs.getString("message"), rs.getString("details")));
                }
            }
        }
        return list;
    }

    public Map<LogLevel, Integer> countByLevel() throws SQLException {
        Map<LogLevel, Integer> counts = new EnumMap<>(LogLevel.class);
        for (LogLevel l : LogLevel.values()) {
            counts.put(l, 0);
        }
        try (Connection c = connect();
             PreparedStatement ps = c.prepareStatement("SELECT level, COUNT(*) FROM logs GROUP BY level");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                counts.put(LogLevel.valueOf(rs.getString(1)), rs.getInt(2));
            }
        }
        return counts;
    }

    /** @return number of deleted rows */
    public int clear() throws SQLException {
        try (Connection c = connect(); PreparedStatement ps = c.prepareStatement("DELETE FROM logs")) {
            return ps.executeUpdate();
        }
    }
}
