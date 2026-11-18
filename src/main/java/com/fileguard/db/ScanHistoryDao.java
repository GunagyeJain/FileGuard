package com.fileguard.db;

import com.fileguard.model.ResultItem;
import com.fileguard.model.ResultStatus;
import com.fileguard.model.ScanReport;
import com.fileguard.model.ScanStatus;
import com.fileguard.model.ScanType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Stores finished scans and their result rows, and reads them back for the History page. */
public class ScanHistoryDao {

    private static final String SCAN_COLUMNS =
            "id, scan_type, folder_path, second_folder, started_at, duration_ms, status, "
                    + "files_scanned, bytes_scanned, issues_found, redundant_bytes, summary";

    private final Database database;

    public ScanHistoryDao(Database database) {
        this.database = database;
    }

    /** Saves the scan and all its result rows in a single transaction. Returns the new id. */
    public long save(ScanReport report) throws SQLException {
        try (Connection conn = database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                long scanId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO scans (scan_type, folder_path, second_folder, started_at, duration_ms, status, "
                                + "files_scanned, bytes_scanned, issues_found, redundant_bytes, summary) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, report.type().name());
                    ps.setString(2, report.folder());
                    ps.setString(3, report.secondFolder());
                    ps.setString(4, report.startedAt().toString());
                    ps.setLong(5, report.durationMs());
                    ps.setString(6, report.status().name());
                    ps.setInt(7, report.filesScanned());
                    ps.setLong(8, report.bytesScanned());
                    ps.setInt(9, report.issuesFound());
                    ps.setLong(10, report.redundantBytes());
                    ps.setString(11, report.summary());
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        scanId = keys.getLong(1);
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO scan_results (scan_id, status, relative_path, detail, size_bytes, group_no) "
                                + "VALUES (?, ?, ?, ?, ?, ?)")) {
                    for (ResultItem item : report.items()) {
                        ps.setLong(1, scanId);
                        ps.setString(2, item.status().name());
                        ps.setString(3, item.path());
                        ps.setString(4, item.detail());
                        ps.setLong(5, item.size());
                        ps.setInt(6, item.group());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                conn.commit();
                return scanId;
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    /** Most recent scans first. Result rows are not loaded (use {@link #findResults}). */
    public List<ScanReport> findRecent(int limit) throws SQLException {
        List<ScanReport> list = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + SCAN_COLUMNS + " FROM scans ORDER BY started_at DESC, id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapScan(rs, List.of()));
                }
            }
        }
        return list;
    }

    public List<ResultItem> findResults(long scanId) throws SQLException {
        List<ResultItem> items = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT status, relative_path, detail, size_bytes, group_no FROM scan_results WHERE scan_id = ? ORDER BY id")) {
            ps.setLong(1, scanId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    items.add(new ResultItem(ResultStatus.valueOf(rs.getString("status")), rs.getString("relative_path"),
                            rs.getString("detail"), rs.getLong("size_bytes"), rs.getInt("group_no")));
                }
            }
        }
        return items;
    }

    /** Loads one scan including its result rows. */
    public Optional<ScanReport> findWithResults(long scanId) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT " + SCAN_COLUMNS + " FROM scans WHERE id = ?")) {
            ps.setLong(1, scanId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(mapScan(rs, findResults(scanId)));
            }
        }
    }

    public int countCompleted() throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM scans WHERE status = ?")) {
            ps.setString(1, ScanStatus.COMPLETED.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    /** Most recent completed scan of the given type, if any (used by the dashboard). */
    public Optional<ScanReport> findLatestCompleted(ScanType type) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT " + SCAN_COLUMNS + " FROM scans WHERE scan_type = ? AND status = ? "
                             + "ORDER BY started_at DESC, id DESC LIMIT 1")) {
            ps.setString(1, type.name());
            ps.setString(2, ScanStatus.COMPLETED.name());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapScan(rs, List.of())) : Optional.empty();
            }
        }
    }

    private static ScanReport mapScan(ResultSet rs, List<ResultItem> items) throws SQLException {
        return new ScanReport(
                rs.getLong("id"),
                ScanType.valueOf(rs.getString("scan_type")),
                rs.getString("folder_path"),
                rs.getString("second_folder"),
                LocalDateTime.parse(rs.getString("started_at")),
                rs.getLong("duration_ms"),
                ScanStatus.valueOf(rs.getString("status")),
                rs.getInt("files_scanned"),
                rs.getLong("bytes_scanned"),
                rs.getInt("issues_found"),
                rs.getLong("redundant_bytes"),
                rs.getString("summary"),
                items);
    }
}
