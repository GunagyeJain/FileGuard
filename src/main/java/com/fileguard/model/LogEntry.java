package com.fileguard.model;

import java.time.LocalDateTime;

/**
 * One activity log record stored by the backend.
 *
 * @param id        database id
 * @param timestamp when the backend received it
 * @param level     INFO, WARN or ERROR
 * @param category  short area name, e.g. SCAN, EXPORT, APP
 * @param message   one-line description
 * @param details   optional extra text (may be empty)
 */
public record LogEntry(long id, LocalDateTime timestamp, LogLevel level, String category, String message, String details) {
}
