package com.fileguard.core;

import com.fileguard.model.FileEntry;
import com.fileguard.model.ResultItem;
import com.fileguard.model.ResultStatus;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Finds files with identical content.
 *
 * Step 1: group files by size - files of different sizes can never be identical.
 * Step 2: hash only files whose size is shared with at least one other file.
 * Step 3: group by SHA-256 hash; every group with 2+ files is a duplicate group.
 *
 * Empty (0 byte) files are ignored because they are trivially "identical".
 */
public class DuplicateFinder {

    /** A set of files that all have the same content. */
    public record DuplicateGroup(String hash, long size, List<String> paths) {
        /** Space that could be saved by keeping only one copy. */
        public long redundantBytes() {
            return size * (paths.size() - 1);
        }
    }

    /** Outcome of a duplicate search. */
    public record Result(List<DuplicateGroup> groups, List<ResultItem> skipped, int filesExamined, long bytesHashed) {
        public long redundantBytes() {
            return groups.stream().mapToLong(DuplicateGroup::redundantBytes).sum();
        }

        public int duplicateFileCount() {
            return groups.stream().mapToInt(g -> g.paths().size() - 1).sum();
        }
    }

    private final FolderScanner scanner = new FolderScanner();

    public Result find(Path folder, ProgressListener progress) throws IOException {
        FolderScanner.Listing listing = scanner.listFiles(folder, progress);

        // Step 1: keep only files whose size appears more than once.
        Map<Long, List<FileEntry>> bySize = listing.files().stream()
                .filter(f -> f.size() > 0)
                .collect(Collectors.groupingBy(FileEntry::size));
        List<FileEntry> candidates = bySize.values().stream()
                .filter(list -> list.size() > 1)
                .flatMap(List::stream)
                .toList();

        // Step 2: hash the candidates.
        FolderScanner.HashResult hashed = scanner.hashFiles(candidates, progress, "Hashing");

        // Step 3: group by hash.
        Map<String, List<FileEntry>> byHash = new LinkedHashMap<>();
        for (FileEntry f : hashed.hashed()) {
            byHash.computeIfAbsent(f.hash(), h -> new ArrayList<>()).add(f);
        }
        List<DuplicateGroup> groups = byHash.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .map(e -> new DuplicateGroup(e.getKey(), e.getValue().get(0).size(),
                        e.getValue().stream().map(FileEntry::relativePath).sorted().toList()))
                .sorted(Comparator.comparingLong(DuplicateGroup::redundantBytes).reversed())
                .toList();

        List<ResultItem> skipped = new ArrayList<>(listing.skipped());
        skipped.addAll(hashed.skipped());
        long bytesHashed = candidates.stream().mapToLong(FileEntry::size).sum();
        return new Result(groups, skipped, listing.files().size(), bytesHashed);
    }

    /** Turns groups into table rows: one row per file, numbered by group. */
    public static List<ResultItem> toItems(List<DuplicateGroup> groups) {
        List<ResultItem> items = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            DuplicateGroup g = groups.get(i);
            String detail = g.paths().size() + " identical copies · SHA-256 " + g.hash().substring(0, 12) + "…";
            for (String path : g.paths()) {
                items.add(new ResultItem(ResultStatus.DUPLICATE, path, detail, g.size(), i + 1));
            }
        }
        return items;
    }
}
