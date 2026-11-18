package com.fileguard.model;

/** How a scan finished. */
public enum ScanStatus {
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    FAILED("Failed");

    private final String label;

    ScanStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
