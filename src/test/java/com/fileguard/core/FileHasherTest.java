package com.fileguard.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.CancellationException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileHasherTest {

    @TempDir
    Path dir;

    @Test
    void hashesKnownValues() throws IOException {
        // Reference values from the SHA-256 specification (FIPS 180-2 test vectors).
        Path abc = Files.writeString(dir.resolve("abc.txt"), "abc");
        Path empty = Files.write(dir.resolve("empty.txt"), new byte[0]);

        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", FileHasher.sha256(abc));
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", FileHasher.sha256(empty));
    }

    @Test
    void largeFileSpanningManyBuffersMatchesInMemoryDigest() throws Exception {
        byte[] data = new byte[FileHasher.BUFFER_SIZE * 5 + 123];
        new Random(42).nextBytes(data);
        Path file = Files.write(dir.resolve("big.bin"), data);

        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        String expected = java.util.HexFormat.of().formatHex(md.digest(data));
        long[] reported = {0};

        assertEquals(expected, FileHasher.sha256(file, ProgressListener.NONE, n -> reported[0] += n));
        assertEquals(data.length, reported[0], "all bytes should be reported to the progress callback");
    }

    @Test
    void differentContentGivesDifferentHash() throws IOException {
        Path a = Files.writeString(dir.resolve("a.txt"), "hello");
        Path b = Files.writeString(dir.resolve("b.txt"), "hellp");
        assertNotEquals(FileHasher.sha256(a), FileHasher.sha256(b));
    }

    @Test
    void stopsWhenCancelled() throws IOException {
        Path file = Files.write(dir.resolve("data.bin"), new byte[FileHasher.BUFFER_SIZE * 3]);
        assertThrows(CancellationException.class,
                () -> FileHasher.sha256(file, TestListeners.cancelled(), n -> { }));
    }
}
