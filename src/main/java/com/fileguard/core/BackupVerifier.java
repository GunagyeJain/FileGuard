package com.fileguard.core;

import com.fileguard.model.FileEntry;
import com.fileguard.model.ResultItem;
import com.fileguard.model.ResultStatus;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Checks that a backup folder contains the same files as the original folder.
 *
 * Files are matched by their path relative to each root folder, e.g.
 * "photos/2024/a.jpg" in the original is compared with "photos/2024/a.jpg"
 * in the backup. To save time, two files with different sizes are reported as
 * different straight away; only files with equal sizes are hashed.
 */
public class BackupVerifier {

    /** Outcome of a verification. */
    public record Result(List<ResultItem> items, int matched, int filesExamined, long bytesHashed) {
    }

    private final FolderScanner scanner = new FolderScanner();

    public Result verify(Path original, Path backup, ProgressListener progress) throws IOException {
        validateFolders(original, backup);
        FolderScanner.Listing originalList = scanner.listFiles(original, progress);
        FolderScanner.Listing backupList = scanner.listFiles(backup, progress);
        Map<String, FileEntry> originalByPath = byPath(originalList.files());
        Map<String, FileEntry> backupByPath = byPath(backupList.files());

        List<ResultItem> items = new ArrayList<>(originalList.skipped());
        items.addAll(backupList.skipped());
        List<String> sameSize = compareSizes(originalList.files(), backupByPath, items);
        addFilesOnlyInBackup(backupList.files(), originalByPath, items);
        int matched = compareContents(sameSize, originalByPath, backupByPath, items, progress);

        items.sort(Comparator.comparing(ResultItem::status).thenComparing(ResultItem::path));
        long bytesHashed = 2 * sameSize.stream().mapToLong(path -> originalByPath.get(path).size()).sum();
        int examined = originalList.files().size() + backupList.files().size();
        return new Result(items, matched, examined, bytesHashed);
    }

    /**
     * Pass 1 (cheap, no file reading): reports files missing from the backup and files
     * whose size differs. Returns the paths whose sizes match, which still need hashing.
     */
    private static List<String> compareSizes(List<FileEntry> originals, Map<String, FileEntry> backupByPath,
                                             List<ResultItem> items) {
        List<String> sameSize = new ArrayList<>();
        for (FileEntry file : originals) {
            FileEntry copy = backupByPath.get(file.relativePath());
            if (copy == null) {
                items.add(new ResultItem(ResultStatus.MISSING, file.relativePath(),
                        "Not found in backup folder", file.size()));
            } else if (copy.size() != file.size()) {
                items.add(new ResultItem(ResultStatus.DIFFERENT, file.relativePath(),
                        "Size differs: original " + Format.bytes(file.size()) + ", backup " + Format.bytes(copy.size()),
                        file.size()));
            } else {
                sameSize.add(file.relativePath());
            }
        }
        return sameSize;
    }

    private static void addFilesOnlyInBackup(List<FileEntry> copies, Map<String, FileEntry> originalByPath,
                                             List<ResultItem> items) {
        for (FileEntry copy : copies) {
            if (!originalByPath.containsKey(copy.relativePath())) {
                items.add(new ResultItem(ResultStatus.EXTRA, copy.relativePath(),
                        "Exists only in backup folder", copy.size()));
            }
        }
    }

    /** Pass 2: hashes both sides of every same-size pair. Returns how many pairs are identical. */
    private int compareContents(List<String> sameSize, Map<String, FileEntry> originalByPath,
                                Map<String, FileEntry> backupByPath, List<ResultItem> items, ProgressListener progress) {
        List<FileEntry> toHash = new ArrayList<>();
        for (String path : sameSize) {
            toHash.add(originalByPath.get(path));
            toHash.add(backupByPath.get(path));
        }
        FolderScanner.HashResult hashResult = scanner.hashFiles(toHash, progress, "Comparing");
        items.addAll(hashResult.skipped());
        Map<Path, String> hashByFile = new HashMap<>();
        hashResult.hashed().forEach(f -> hashByFile.put(f.absolutePath(), f.hash()));

        int matched = 0;
        for (String path : sameSize) {
            String a = hashByFile.get(originalByPath.get(path).absolutePath());
            String b = hashByFile.get(backupByPath.get(path).absolutePath());
            if (a == null || b == null) {
                continue; // one side unreadable, already listed as skipped
            }
            if (a.equals(b)) {
                matched++;
            } else {
                items.add(new ResultItem(ResultStatus.DIFFERENT, path,
                        "Same size but different content (SHA-256 mismatch)", originalByPath.get(path).size()));
            }
        }
        return matched;
    }

    private static Map<String, FileEntry> byPath(List<FileEntry> files) {
        Map<String, FileEntry> map = new HashMap<>();
        files.forEach(f -> map.put(f.relativePath(), f));
        return map;
    }

    /**
     * Rejects choices that would make the comparison meaningless, such as
     * picking the same folder twice or a backup located inside the original.
     */
    static void validateFolders(Path original, Path backup) throws IOException {
        if (!Files.isDirectory(original)) {
            throw new IOException("Original folder does not exist: " + original);
        }
        if (!Files.isDirectory(backup)) {
            throw new IOException("Backup folder does not exist: " + backup);
        }
        Path a = original.toRealPath();
        Path b = backup.toRealPath();
        if (a.equals(b)) {
            throw new IllegalArgumentException("The original and backup folders are the same folder.");
        }
        if (b.startsWith(a) || a.startsWith(b)) {
            throw new IllegalArgumentException("One folder is inside the other. Please choose two separate folders.");
        }
    }
}
