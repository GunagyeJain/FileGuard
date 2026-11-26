package com.fileguard.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * The outcome of one scan. The same object is shown in the UI, saved to the
 * scan history and exported to CSV.
 *
 * @param id             database id (0 until saved)
 * @param type           kind of scan
 * @param folder         scanned folder (the original folder for backup checks)
 * @param secondFolder   backup folder for backup checks, otherwise null
 * @param startedAt      when the scan started
 * @param durationMs     how long it took
 * @param status         completed / cancelled / failed
 * @param filesScanned   number of files examined
 * @param bytesScanned   number of bytes read for hashing
 * @param issuesFound    number of differences/findings (unreadable files not counted)
 * @param redundantBytes wasted space from duplicates (duplicate search only)
 * @param summary        one-line human readable summary
 * @param items          detailed result rows (empty when loaded for the history list)
 */
public record ScanReport(long id, ScanType type, String folder, String secondFolder,
                         LocalDateTime startedAt, long durationMs, ScanStatus status,
                         int filesScanned, long bytesScanned, int issuesFound, long redundantBytes,
                         String summary, List<ResultItem> items) {

    public ScanReport {
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** Counts findings, ignoring files that were skipped because they could not be read. */
    public static int countIssues(List<ResultItem> items) {
        return (int) items.stream().filter(i -> i.status() != ResultStatus.SKIPPED).count();
    }

    public long countOf(ResultStatus status) {
        return items.stream().filter(i -> i.status() == status).count();
    }

    public ScanReport withId(long newId) {
        return new ScanReport(newId, type, folder, secondFolder, startedAt, durationMs, status,
                filesScanned, bytesScanned, issuesFound, redundantBytes, summary, items);
    }
}
