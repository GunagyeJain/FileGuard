package com.fileguard.db;

import com.fileguard.model.BaselineInfo;
import com.fileguard.model.FileEntry;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Reads and writes baselines (a folder's file list with SHA-256 hashes). */
public class BaselineDao {

    private final Database database;

    public BaselineDao(Database database) {
        this.database = database;
    }

    /**
     * Saves a new baseline for the folder, replacing any previous one.
     * Everything runs in one transaction: either the whole baseline is saved or nothing.
     */
    public BaselineInfo saveBaseline(String folderPath, List<FileEntry> files, LocalDateTime createdAt) throws SQLException {
        long totalBytes = files.stream().mapToLong(FileEntry::size).sum();
        try (Connection conn = database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement del = conn.prepareStatement("DELETE FROM baselines WHERE folder_path = ?")) {
                    del.setString(1, folderPath);
                    del.executeUpdate(); // baseline_files rows are removed by ON DELETE CASCADE
                }

                long baselineId;
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO baselines (folder_path, created_at, file_count, total_bytes) VALUES (?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ins.setString(1, folderPath);
                    ins.setString(2, createdAt.toString());
                    ins.setInt(3, files.size());
                    ins.setLong(4, totalBytes);
                    ins.executeUpdate();
                    try (ResultSet keys = ins.getGeneratedKeys()) {
                        keys.next();
                        baselineId = keys.getLong(1);
                    }
                }

                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO baseline_files (baseline_id, relative_path, size_bytes, last_modified, sha256) VALUES (?, ?, ?, ?, ?)")) {
                    for (FileEntry f : files) {
                        ins.setLong(1, baselineId);
                        ins.setString(2, f.relativePath());
                        ins.setLong(3, f.size());
                        ins.setLong(4, f.lastModified());
                        ins.setString(5, f.hash());
                        ins.addBatch();
                    }
                    ins.executeBatch();
                }
                conn.commit();
                return new BaselineInfo(baselineId, folderPath, createdAt, files.size(), totalBytes);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public Optional<BaselineInfo> findByFolder(String folderPath) throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, folder_path, created_at, file_count, total_bytes FROM baselines WHERE folder_path = ?")) {
            ps.setString(1, folderPath);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapInfo(rs)) : Optional.empty();
            }
        }
    }

    public List<BaselineInfo> findAll() throws SQLException {
        List<BaselineInfo> list = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, folder_path, created_at, file_count, total_bytes FROM baselines ORDER BY created_at DESC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapInfo(rs));
            }
        }
        return list;
    }

    /** Loads the stored files of a baseline. The absolute path is rebuilt from the folder. */
    public List<FileEntry> loadFiles(BaselineInfo baseline) throws SQLException {
        Path root = Path.of(baseline.folderPath());
        List<FileEntry> files = new ArrayList<>();
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT relative_path, size_bytes, last_modified, sha256 FROM baseline_files WHERE baseline_id = ?")) {
            ps.setLong(1, baseline.id());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String rel = rs.getString("relative_path");
                    files.add(new FileEntry(rel, root.resolve(rel), rs.getLong("size_bytes"),
                            rs.getLong("last_modified"), rs.getString("sha256")));
                }
            }
        }
        return files;
    }

    public int count() throws SQLException {
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM baselines");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private static BaselineInfo mapInfo(ResultSet rs) throws SQLException {
        return new BaselineInfo(rs.getLong("id"), rs.getString("folder_path"),
                LocalDateTime.parse(rs.getString("created_at")), rs.getInt("file_count"), rs.getLong("total_bytes"));
    }
}
