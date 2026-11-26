package com.fileguard.service;

import com.fileguard.core.BackupVerifier;
import com.fileguard.core.DuplicateFinder;
import com.fileguard.core.FolderScanner;
import com.fileguard.core.Format;
import com.fileguard.core.IntegrityChecker;
import com.fileguard.core.ProgressListener;
import com.fileguard.db.BaselineDao;
import com.fileguard.db.Database;
import com.fileguard.db.ScanHistoryDao;
import com.fileguard.model.BaselineInfo;
import com.fileguard.model.FileEntry;
import com.fileguard.model.LogLevel;
import com.fileguard.model.ResultItem;
import com.fileguard.model.ResultStatus;
import com.fileguard.model.ScanReport;
import com.fileguard.model.ScanStatus;
import com.fileguard.model.ScanType;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

/**
 * Connects the file-processing classes with the database. Each public scan
 * method runs a complete scan, saves it to the history and returns the report.
 *
 * These methods are slow and must be called from a background thread
 * (the UI does this with a JavaFX Task).
 */
public class ScanService {

    /** The part of a report that each scan type fills in. */
    private record Outcome(int filesScanned, long bytesScanned, long redundantBytes,
                           String summary, List<ResultItem> items) {
    }

    @FunctionalInterface
    private interface ScanBody {
        Outcome run() throws IOException, SQLException;
    }

    private final BaselineDao baselineDao;
    private final ScanHistoryDao historyDao;
    private final FolderScanner scanner = new FolderScanner();
    private final ActivityLogger logger;

    public ScanService(Database database) {
        this(database, ActivityLogger.NONE);
    }

    /** @param logger receives scan events; the app passes the backend client here */
    public ScanService(Database database, ActivityLogger logger) {
        this.baselineDao = new BaselineDao(database);
        this.historyDao = new ScanHistoryDao(database);
        this.logger = logger;
    }

    // ------------------------------------------------------------------ scans

    /** Hashes every file in the folder and stores the result as the folder's baseline. */
    public ScanReport createBaseline(Path folder, ProgressListener progress) throws IOException, SQLException {
        String key = folderKey(folder);
        return execute(ScanType.BASELINE, key, null, progress, () -> {
            FolderScanner.Listing listing = scanner.listFiles(Path.of(key), progress);
            FolderScanner.HashResult hashed = scanner.hashFiles(listing.files(), progress, "Hashing");
            progress.checkCancelled();
            progress.updateMessage("Saving baseline...");
            baselineDao.saveBaseline(key, hashed.hashed(), now());

            List<ResultItem> skipped = combine(listing.skipped(), hashed.skipped());
            String summary = "Baseline saved with " + Format.count(hashed.hashed().size(), "file")
                    + " (" + Format.bytes(listing.totalBytes()) + ")"
                    + (skipped.isEmpty() ? "" : ", " + skipped.size() + " unreadable item(s) skipped");
            return new Outcome(hashed.hashed().size(), listing.totalBytes(), 0, summary, skipped);
        });
    }

    /** Scans the folder again and compares it with the saved baseline. */
    public ScanReport checkIntegrity(Path folder, ProgressListener progress) throws IOException, SQLException {
        String key = folderKey(folder);
        BaselineInfo baseline = baselineDao.findByFolder(key)
                .orElseThrow(() -> new IllegalStateException(
                        "No baseline exists for this folder yet. Create a baseline first."));

        return execute(ScanType.INTEGRITY, key, null, progress, () -> {
            List<FileEntry> before = baselineDao.loadFiles(baseline);
            FolderScanner.Listing listing = scanner.listFiles(Path.of(key), progress);
            FolderScanner.HashResult hashed = scanner.hashFiles(listing.files(), progress, "Checking");
            progress.checkCancelled();

            // Files that exist but could not be read must not be reported as "deleted".
            List<ResultItem> skipped = combine(listing.skipped(), hashed.skipped());
            Set<String> unreadable = skipped.stream().map(ResultItem::path).collect(Collectors.toSet());
            List<ResultItem> items = new ArrayList<>(IntegrityChecker.compare(before, hashed.hashed(), unreadable));
            items.addAll(skipped);

            long added = count(items, ResultStatus.ADDED);
            long modified = count(items, ResultStatus.MODIFIED);
            long deleted = count(items, ResultStatus.DELETED);
            long unchanged = hashed.hashed().size() - added - modified;
            String summary = added + " added, " + modified + " modified, " + deleted + " deleted, "
                    + unchanged + " unchanged (baseline from " + Format.dateTime(baseline.createdAt()) + ")";
            return new Outcome(hashed.hashed().size(), listing.totalBytes(), 0, summary, items);
        });
    }

    /** Compares an original folder with its backup by relative path and content. */
    public ScanReport verifyBackup(Path original, Path backup, ProgressListener progress) throws IOException, SQLException {
        String originalKey = folderKey(original);
        String backupKey = folderKey(backup);
        return execute(ScanType.BACKUP, originalKey, backupKey, progress, () -> {
            BackupVerifier.Result result = new BackupVerifier().verify(Path.of(originalKey), Path.of(backupKey), progress);
            String summary = result.matched() + " matching, " + count(result.items(), ResultStatus.MISSING) + " missing, "
                    + count(result.items(), ResultStatus.DIFFERENT) + " different, "
                    + count(result.items(), ResultStatus.EXTRA) + " only in backup";
            return new Outcome(result.filesExamined(), result.bytesHashed(), 0, summary, result.items());
        });
    }

    /** Finds groups of identical files inside one folder. */
    public ScanReport findDuplicates(Path folder, ProgressListener progress) throws IOException, SQLException {
        String key = folderKey(folder);
        return execute(ScanType.DUPLICATES, key, null, progress, () -> {
            DuplicateFinder.Result result = new DuplicateFinder().find(Path.of(key), progress);
            List<ResultItem> items = new ArrayList<>(DuplicateFinder.toItems(result.groups()));
            items.addAll(result.skipped());
            String summary = Format.count(result.groups().size(), "duplicate group") + ", "
                    + Format.count(result.duplicateFileCount(), "redundant copy", "redundant copies")
                    + ", " + Format.bytes(result.redundantBytes()) + " redundant";
            return new Outcome(result.filesExamined(), result.bytesHashed(), result.redundantBytes(), summary, items);
        });
    }

    // ---------------------------------------------------------------- queries

    public Optional<BaselineInfo> findBaseline(Path folder) throws IOException, SQLException {
        return baselineDao.findByFolder(folderKey(folder));
    }

    public List<BaselineInfo> listBaselines() throws SQLException {
        return baselineDao.findAll();
    }

    public List<ScanReport> recentScans(int limit) throws SQLException {
        return historyDao.findRecent(limit);
    }

    public Optional<ScanReport> loadScan(long id) throws SQLException {
        return historyDao.findWithResults(id);
    }

    public int completedScanCount() throws SQLException {
        return historyDao.countCompleted();
    }

    public int baselineCount() throws SQLException {
        return baselineDao.count();
    }

    public Optional<ScanReport> latestCompleted(ScanType type) throws SQLException {
        return historyDao.findLatestCompleted(type);
    }

    // ---------------------------------------------------------------- helpers

    /**
     * Runs a scan body, measures its duration and stores the report in the history.
     * If the user cancels, a "Cancelled" entry is recorded and the cancellation is
     * passed on so the UI knows the scan stopped. Validation errors (wrong folder,
     * no baseline, ...) are not recorded because no scan actually took place.
     */
    private ScanReport execute(ScanType type, String folder, String secondFolder,
                               ProgressListener progress, ScanBody body) throws IOException, SQLException {
        LocalDateTime startedAt = now();
        long startNanos = System.nanoTime();
        String target = secondFolder == null ? folder : folder + " -> " + secondFolder;
        logger.log(LogLevel.INFO, "SCAN", type.label() + " started", target);
        try {
            Outcome outcome = body.run();
            progress.updateMessage("Saving results...");
            ScanReport report = new ScanReport(0, type, folder, secondFolder, startedAt, elapsedMs(startNanos),
                    ScanStatus.COMPLETED, outcome.filesScanned(), outcome.bytesScanned(),
                    ScanReport.countIssues(outcome.items()), outcome.redundantBytes(), outcome.summary(), outcome.items());
            ScanReport saved = report.withId(historyDao.save(report));
            logger.log(LogLevel.INFO, "SCAN", type.label() + " completed: " + outcome.summary(),
                    target + " (" + Format.duration(saved.durationMs()) + ")");
            return saved;
        } catch (CancellationException e) {
            ScanReport cancelled = new ScanReport(0, type, folder, secondFolder, startedAt, elapsedMs(startNanos),
                    ScanStatus.CANCELLED, 0, 0, 0, 0, "Cancelled by user before finishing", List.of());
            try {
                historyDao.save(cancelled);
            } catch (SQLException saveError) {
                // Keep reporting the cancellation; failing to record it must not turn it into an error.
                logger.log(LogLevel.ERROR, "SCAN", "Could not record cancelled scan: " + saveError.getMessage(), target);
            }
            logger.log(LogLevel.WARN, "SCAN", type.label() + " cancelled by user", target);
            throw e;
        } catch (IOException | SQLException | RuntimeException e) {
            logger.log(LogLevel.ERROR, "SCAN", type.label() + " failed: " + e.getMessage(), target);
            throw e;
        }
    }

    /**
     * The same folder must always map to the same database key, regardless of how it
     * was typed (relative path, different letter case on Windows, trailing "..", ...).
     */
    static String folderKey(Path folder) throws IOException {
        if (folder == null) {
            throw new IllegalArgumentException("Please choose a folder first.");
        }
        try {
            return folder.toRealPath().toString();
        } catch (IOException e) {
            throw new IOException("Folder not found or not accessible: " + folder, e);
        }
    }

    private static long count(List<ResultItem> items, ResultStatus status) {
        return items.stream().filter(i -> i.status() == status).count();
    }

    private static List<ResultItem> combine(List<ResultItem> a, List<ResultItem> b) {
        List<ResultItem> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    private static long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
