package com.fileguard.model;

import java.time.LocalDateTime;

/** Summary of a saved baseline (the file list itself is stored separately). */
public record BaselineInfo(long id, String folderPath, LocalDateTime createdAt, int fileCount, long totalBytes) {
}
