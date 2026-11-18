package com.fileguard.core;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.LongConsumer;

/**
 * Calculates SHA-256 hashes using the Java Security API.
 *
 * Files are read in small chunks, so even very large files never have to fit
 * in memory. Cancellation is checked after every chunk so that pressing
 * Cancel stops quickly, even in the middle of a multi-gigabyte file.
 */
public final class FileHasher {

    /** 64 KB read buffer - a good balance between speed and memory use. */
    public static final int BUFFER_SIZE = 64 * 1024;

    private FileHasher() {
    }

    public static String sha256(Path file) throws IOException {
        return sha256(file, ProgressListener.NONE, bytes -> { });
    }

    /**
     * @param file        file to hash
     * @param progress    used only to check for cancellation
     * @param onBytesRead called with the number of bytes read after each chunk
     * @return lowercase hexadecimal SHA-256 digest (64 characters)
     */
    public static String sha256(Path file, ProgressListener progress, LongConsumer onBytesRead) throws IOException {
        MessageDigest digest = newSha256();
        // FileInputStream is used on purpose: unlike NIO channels it is not closed
        // when the thread is interrupted, so cancellation is handled only by our own check.
        try (InputStream in = new FileInputStream(file.toFile())) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = in.read(buffer)) != -1) {
                progress.checkCancelled();
                digest.update(buffer, 0, read);
                onBytesRead.accept(read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            // Every Java implementation is required to support SHA-256.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
