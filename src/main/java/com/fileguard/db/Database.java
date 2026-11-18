package com.fileguard.db;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

/**
 * Owns the location of the SQLite database file and creates the tables the
 * first time the application runs.
 *
 * A new connection is opened for each operation and closed with
 * try-with-resources. For a small desktop app this is simple and safe, and
 * SQLite connections are cheap to open.
 */
public class Database {

    private final Path file;
    private final String url;

    public Database(Path file) {
        this.file = file;
        this.url = "jdbc:sqlite:" + file.toAbsolutePath();
    }

    /**
     * Default location: a ".fileguard" folder in the user's home directory, so the
     * database survives restarts and is never inside the project folder.
     * Can be overridden with -Dfileguard.db=path/to/file.db
     */
    public static Database openDefault() throws IOException, SQLException {
        String override = System.getProperty("fileguard.db");
        Path path = override != null
                ? Path.of(override)
                : Path.of(System.getProperty("user.home"), ".fileguard", "fileguard.db");
        Database db = new Database(path);
        db.initialize();
        return db;
    }

    public Path getFile() {
        return file;
    }

    public Connection getConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("foreign_keys", "true"); // needed for ON DELETE CASCADE
        return DriverManager.getConnection(url, props);
    }

    /** Creates the folder and tables if they do not exist yet. Safe to call every start-up. */
    public void initialize() throws IOException, SQLException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (Connection conn = getConnection(); Statement st = conn.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS baselines (
                        id           INTEGER PRIMARY KEY AUTOINCREMENT,
                        folder_path  TEXT    NOT NULL UNIQUE,
                        created_at   TEXT    NOT NULL,
                        file_count   INTEGER NOT NULL,
                        total_bytes  INTEGER NOT NULL
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS baseline_files (
                        id            INTEGER PRIMARY KEY AUTOINCREMENT,
                        baseline_id   INTEGER NOT NULL REFERENCES baselines(id) ON DELETE CASCADE,
                        relative_path TEXT    NOT NULL,
                        size_bytes    INTEGER NOT NULL,
                        last_modified INTEGER NOT NULL,
                        sha256        TEXT    NOT NULL,
                        UNIQUE (baseline_id, relative_path)
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS scans (
                        id              INTEGER PRIMARY KEY AUTOINCREMENT,
                        scan_type       TEXT    NOT NULL,
                        folder_path     TEXT    NOT NULL,
                        second_folder   TEXT,
                        started_at      TEXT    NOT NULL,
                        duration_ms     INTEGER NOT NULL,
                        status          TEXT    NOT NULL,
                        files_scanned   INTEGER NOT NULL,
                        bytes_scanned   INTEGER NOT NULL,
                        issues_found    INTEGER NOT NULL,
                        redundant_bytes INTEGER NOT NULL,
                        summary         TEXT
                    )""");
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS scan_results (
                        id            INTEGER PRIMARY KEY AUTOINCREMENT,
                        scan_id       INTEGER NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
                        status        TEXT    NOT NULL,
                        relative_path TEXT    NOT NULL,
                        detail        TEXT,
                        size_bytes    INTEGER NOT NULL,
                        group_no      INTEGER NOT NULL
                    )""");
            st.executeUpdate("CREATE INDEX IF NOT EXISTS idx_scan_results_scan ON scan_results(scan_id)");
        }
    }
}
