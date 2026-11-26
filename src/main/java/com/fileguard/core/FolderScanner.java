package com.fileguard.core;

import com.fileguard.model.FileEntry;
import com.fileguard.model.ResultItem;
import com.fileguard.model.ResultStatus;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Walks a folder tree to list its files and hashes lists of files while
 * reporting progress. Files or folders that cannot be read are not fatal:
 * they are collected as {@link ResultStatus#SKIPPED} items so the user can
 * see what was left out.
 */
public class FolderScanner {

    /** Result of listing a folder. */
    public record Listing(List<FileEntry> files, List<ResultItem> skipped) {
        public long totalBytes() {
            return files.stream().mapToLong(FileEntry::size).sum();
        }
    }

    /** Result of hashing a list of files. */
    public record HashResult(List<FileEntry> hashed, List<ResultItem> skipped) {
    }

    private static final int LISTING_UPDATE_EVERY = 200;

    /**
     * Lists every regular file below {@code root}. Symbolic links are not followed,
     * which also prevents endless loops.
     */
    public Listing listFiles(Path root, ProgressListener progress) throws IOException {
        if (!Files.exists(root)) {
            throw new IOException("Folder does not exist: " + root);
        }
        if (!Files.isDirectory(root)) {
            throw new NotDirectoryException(root.toString());
        }

        List<FileEntry> files = new ArrayList<>();
        List<ResultItem> skipped = new ArrayList<>();
        progress.updateProgress(-1);
        progress.updateMessage("Listing files in " + root.getFileName() + "...");

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                progress.checkCancelled();
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    files.add(new FileEntry(relativePath(root, file), file, attrs.size(),
                            attrs.lastModifiedTime().toMillis(), null));
                    if (files.size() % LISTING_UPDATE_EVERY == 0) {
                        progress.checkCancelled();
                        progress.updateMessage("Listing files... " + files.size() + " found");
                    }
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // Typically a permission problem or a file deleted during the scan.
                skipped.add(new ResultItem(ResultStatus.SKIPPED, relativePath(root, file),
                        "Cannot access " + file + " (" + describe(exc) + ")", 0));
                return FileVisitResult.CONTINUE;
            }
        });

        files.sort(Comparator.comparing(FileEntry::relativePath));
        return new Listing(files, skipped);
    }

    /**
     * Calculates the SHA-256 hash of every file. Progress is based on bytes read,
     * so one large file moves the bar as smoothly as many small ones.
     *
     * @param phase short description shown in the status line, e.g. "Hashing"
     */
    public HashResult hashFiles(List<FileEntry> files, ProgressListener progress, String phase) {
        long totalBytes = files.stream().mapToLong(FileEntry::size).sum();
        long[] bytesDone = {0};
        List<FileEntry> hashed = new ArrayList<>(files.size());
        List<ResultItem> skipped = new ArrayList<>();

        for (int i = 0; i < files.size(); i++) {
            progress.checkCancelled();
            FileEntry entry = files.get(i);
            progress.updateMessage(phase + " " + (i + 1) + " of " + files.size() + ": " + entry.relativePath());
            final int index = i;
            try {
                String hash = FileHasher.sha256(entry.absolutePath(), progress, read -> {
                    bytesDone[0] += read;
                    progress.updateProgress(fraction(bytesDone[0], totalBytes, index, files.size()));
                });
                hashed.add(entry.withHash(hash));
            } catch (IOException e) {
                skipped.add(new ResultItem(ResultStatus.SKIPPED, entry.relativePath(),
                        "Could not read " + entry.absolutePath() + " (" + describe(e) + ")", entry.size()));
            }
            progress.updateProgress(fraction(bytesDone[0], totalBytes, i + 1, files.size()));
        }
        return new HashResult(hashed, skipped);
    }

    /** Converts an absolute path into a portable relative path using '/'. */
    static String relativePath(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    private static double fraction(long bytesDone, long totalBytes, int filesDone, int totalFiles) {
        if (totalBytes > 0) {
            return Math.min(1.0, (double) bytesDone / totalBytes);
        }
        // All files are empty: fall back to counting files.
        return totalFiles == 0 ? 1.0 : (double) filesDone / totalFiles;
    }

    private static String describe(IOException e) {
        if (e instanceof AccessDeniedException) {
            return "access denied";
        }
        if (e instanceof NoSuchFileException) {
            return "file no longer exists";
        }
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }
}
