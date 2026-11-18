package com.fileguard.ui;

/** The pages reachable from the sidebar. */
public enum Page {
    DASHBOARD("Dashboard", Icons.DASHBOARD),
    INTEGRITY("File integrity", Icons.SHIELD),
    BACKUP("Backup verification", Icons.BACKUP),
    DUPLICATES("Duplicate files", Icons.COPY),
    HISTORY("Scan history", Icons.HISTORY),
    SETTINGS("Settings", Icons.SETTINGS);

    private final String title;
    private final String icon;

    Page(String title, String icon) {
        this.title = title;
        this.icon = icon;
    }

    public String title() {
        return title;
    }

    public String icon() {
        return icon;
    }
}
