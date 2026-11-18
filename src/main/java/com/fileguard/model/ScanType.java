package com.fileguard.model;

/** The different kinds of scans FileGuard can run. */
public enum ScanType {
    BASELINE("Baseline created"),
    INTEGRITY("Integrity check"),
    BACKUP("Backup verification"),
    DUPLICATES("Duplicate search");

    private final String label;

    ScanType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
