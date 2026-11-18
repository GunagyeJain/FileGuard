package com.fileguard.core;

import com.fileguard.model.FileEntry;
import com.fileguard.model.ResultItem;
import com.fileguard.model.ResultStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tests for integrity comparison, backup verification and duplicate detection. */
class ComparisonTest {

    @TempDir
    Path dir;

    private static FileEntry entry(String path, long size, String hash) {
        return new FileEntry(path, Path.of(path), size, 0, hash);
    }

    private static ResultStatus statusOf(List<ResultItem> items, String path) {
        return items.stream().filter(i -> i.path().equals(path)).findFirst().map(ResultItem::status).orElse(null);
    }

    // ------------------------------------------------------------ integrity

    @Test
    void detectsAddedModifiedAndDeleted() {
        List<FileEntry> baseline = List.of(entry("same.txt", 3, "h1"), entry("edit.txt", 3, "h2"), entry("gone.txt", 3, "h3"));
        List<FileEntry> current = List.of(entry("same.txt", 3, "h1"), entry("edit.txt", 4, "h2x"), entry("new.txt", 1, "h4"));

        List<ResultItem> items = IntegrityChecker.compare(baseline, current, Set.of());

        assertEquals(3, items.size());
        assertEquals(ResultStatus.ADDED, statusOf(items, "new.txt"));
        assertEquals(ResultStatus.MODIFIED, statusOf(items, "edit.txt"));
        assertEquals(ResultStatus.DELETED, statusOf(items, "gone.txt"));
    }

    @Test
    void unreadableFileIsNotReportedAsDeleted() {
        List<FileEntry> baseline = List.of(entry("locked.db", 10, "h1"));
        List<ResultItem> items = IntegrityChecker.compare(baseline, List.of(), Set.of("locked.db"));
        assertTrue(items.isEmpty());
    }

    @Test
    void identicalStateHasNoDifferences() {
        List<FileEntry> files = List.of(entry("a", 1, "x"), entry("b/c", 2, "y"));
        assertTrue(IntegrityChecker.compare(files, files, Set.of()).isEmpty());
    }

    // ------------------------------------------------------------ backup

    @Test
    void backupVerificationFindsMissingDifferentAndExtra() throws IOException {
        Path original = Files.createDirectories(dir.resolve("orig"));
        Path backup = Files.createDirectories(dir.resolve("backup"));
        write(original, "docs/same.txt", "identical");
        write(backup, "docs/same.txt", "identical");
        write(original, "docs/samesize.txt", "AAAA");
        write(backup, "docs/samesize.txt", "BBBB");          // same size, different content
        write(original, "sizediff.txt", "short");
        write(backup, "sizediff.txt", "much longer text");   // different size
        write(original, "only-original.txt", "x");
        write(backup, "only-backup.txt", "y");

        BackupVerifier.Result result = new BackupVerifier().verify(original, backup, ProgressListener.NONE);

        assertEquals(1, result.matched());
        assertEquals(ResultStatus.DIFFERENT, statusOf(result.items(), "docs/samesize.txt"));
        assertEquals(ResultStatus.DIFFERENT, statusOf(result.items(), "sizediff.txt"));
        assertEquals(ResultStatus.MISSING, statusOf(result.items(), "only-original.txt"));
        assertEquals(ResultStatus.EXTRA, statusOf(result.items(), "only-backup.txt"));
        assertEquals(4, result.items().size());
    }

    @Test
    void backupVerificationRejectsSameOrNestedFolders() throws IOException {
        Path original = Files.createDirectories(dir.resolve("orig"));
        Path nested = Files.createDirectories(original.resolve("inner"));
        BackupVerifier verifier = new BackupVerifier();
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(original, original, ProgressListener.NONE));
        assertThrows(IllegalArgumentException.class, () -> verifier.verify(original, nested, ProgressListener.NONE));
    }

    @Test
    void emptyFoldersMatch() throws IOException {
        Path a = Files.createDirectories(dir.resolve("a"));
        Path b = Files.createDirectories(dir.resolve("b"));
        BackupVerifier.Result result = new BackupVerifier().verify(a, b, ProgressListener.NONE);
        assertTrue(result.items().isEmpty());
        assertEquals(0, result.matched());
    }

    // ------------------------------------------------------------ duplicates

    @Test
    void findsDuplicateGroupsAndRedundantBytes() throws IOException {
        write(dir, "a/photo.jpg", "1234567890");
        write(dir, "b/photo-copy.jpg", "1234567890");
        write(dir, "c/photo-copy2.jpg", "1234567890");
        write(dir, "other.txt", "0987654321");   // same size, different content -> not duplicate
        write(dir, "x.txt", "hi");
        write(dir, "y.txt", "hi");
        write(dir, "empty1.txt", "");
        write(dir, "empty2.txt", "");             // empty files are ignored

        DuplicateFinder.Result result = new DuplicateFinder().find(dir, ProgressListener.NONE);

        assertEquals(2, result.groups().size());
        DuplicateFinder.DuplicateGroup biggest = result.groups().get(0);
        assertEquals(3, biggest.paths().size());
        assertEquals(20, biggest.redundantBytes());           // 2 extra copies x 10 bytes
        assertEquals(22, result.redundantBytes());            // + 1 extra copy x 2 bytes
        assertEquals(3, result.duplicateFileCount());
        assertEquals(5, DuplicateFinder.toItems(result.groups()).size());
    }

    @Test
    void noDuplicatesInUniqueFolder() throws IOException {
        write(dir, "a.txt", "one");
        write(dir, "b.txt", "two!");
        assertTrue(new DuplicateFinder().find(dir, ProgressListener.NONE).groups().isEmpty());
    }

    @Test
    void listingRejectsMissingFolder() {
        assertThrows(IOException.class, () -> new FolderScanner().listFiles(dir.resolve("nope"), ProgressListener.NONE));
    }

    static void write(Path root, String rel, String content) throws IOException {
        Path file = root.resolve(rel);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
