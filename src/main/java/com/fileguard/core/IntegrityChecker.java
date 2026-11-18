package com.fileguard.core;

import com.fileguard.model.FileEntry;
import com.fileguard.model.ResultItem;
import com.fileguard.model.ResultStatus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compares a saved baseline with the current state of a folder.
 *
 * A file is reported as MODIFIED when its SHA-256 hash is different. This only
 * means the content changed - FileGuard cannot know whether the change was an
 * intended edit or unwanted damage.
 */
public final class IntegrityChecker {

    private IntegrityChecker() {
    }

    /**
     * @param baseline   files (with hashes) recorded in the baseline
     * @param current    files (with hashes) found now
     * @param unreadable relative paths that exist now but could not be read; these are
     *                   not reported as deleted, because we simply do not know their state
     * @return added, modified and deleted files, sorted by status then path
     */
    public static List<ResultItem> compare(List<FileEntry> baseline, List<FileEntry> current, Set<String> unreadable) {
        Map<String, FileEntry> before = toMap(baseline);
        Map<String, FileEntry> now = toMap(current);
        List<ResultItem> results = new ArrayList<>();

        for (FileEntry file : now.values()) {
            FileEntry old = before.get(file.relativePath());
            if (old == null) {
                results.add(new ResultItem(ResultStatus.ADDED, file.relativePath(),
                        "New file since baseline", file.size()));
            } else if (!old.hash().equals(file.hash())) {
                results.add(new ResultItem(ResultStatus.MODIFIED, file.relativePath(),
                        describeChange(old, file), file.size()));
            }
        }

        for (FileEntry old : before.values()) {
            if (!now.containsKey(old.relativePath()) && !unreadable.contains(old.relativePath())) {
                results.add(new ResultItem(ResultStatus.DELETED, old.relativePath(),
                        "File no longer present", old.size()));
            }
        }

        results.sort(Comparator.comparing(ResultItem::status).thenComparing(ResultItem::path));
        return results;
    }

    private static String describeChange(FileEntry old, FileEntry now) {
        if (old.size() != now.size()) {
            return "Content changed (size " + Format.bytes(old.size()) + " → " + Format.bytes(now.size()) + ")";
        }
        return "Content changed (same size, different hash)";
    }

    private static Map<String, FileEntry> toMap(List<FileEntry> files) {
        Map<String, FileEntry> map = new LinkedHashMap<>();
        for (FileEntry f : files) {
            map.put(f.relativePath(), f);
        }
        return map;
    }
}
