package com.fileguard.model;

/** Category of a single row in a scan result. */
public enum ResultStatus {
    // Integrity check
    ADDED("Added"),
    MODIFIED("Modified"),
    DELETED("Deleted"),
    // Backup verification
    MISSING("Missing in backup"),
    DIFFERENT("Content differs"),
    EXTRA("Only in backup"),
    // Duplicate search
    DUPLICATE("Duplicate"),
    // Any scan: a file that could not be read (permissions, locked, ...)
    SKIPPED("Skipped");

    private final String label;

    ResultStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
