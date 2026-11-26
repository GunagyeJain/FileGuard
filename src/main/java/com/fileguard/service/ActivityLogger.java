package com.fileguard.service;

import com.fileguard.model.LogLevel;

/**
 * Receives activity events (scan started, report exported, errors, ...).
 * The desktop app sends them to the local backend; tests use {@link #NONE}.
 */
@FunctionalInterface
public interface ActivityLogger {

    ActivityLogger NONE = (level, category, message, details) -> { };

    void log(LogLevel level, String category, String message, String details);

    default void info(String category, String message) {
        log(LogLevel.INFO, category, message, "");
    }

    default void warn(String category, String message) {
        log(LogLevel.WARN, category, message, "");
    }

    default void error(String category, String message, String details) {
        log(LogLevel.ERROR, category, message, details);
    }
}
