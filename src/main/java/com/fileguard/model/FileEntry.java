package com.fileguard.model;

import java.nio.file.Path;

/**
 * One file found while scanning a folder.
 *
 * @param relativePath  path relative to the scanned root, always using '/' as separator
 * @param absolutePath  full path on disk (used to read the file)
 * @param size          file size in bytes
 * @param lastModified  last-modified time in epoch milliseconds
 * @param hash          SHA-256 hex string, or null if not hashed yet
 */
public record FileEntry(String relativePath, Path absolutePath, long size, long lastModified, String hash) {

    /** Returns a copy of this entry with the given hash (records are immutable). */
    public FileEntry withHash(String newHash) {
        return new FileEntry(relativePath, absolutePath, size, lastModified, newHash);
    }
}
