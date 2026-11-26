package com.fileguard.ui.components;

import com.fileguard.model.LogLevel;
import com.fileguard.model.ResultStatus;
import com.fileguard.model.ScanStatus;
import javafx.scene.control.Label;

/** Creates the small outlined labels used in tables (GitHub "Label" style). */
public final class StatusBadge {

    private StatusBadge() {
    }

    public static Label forResult(ResultStatus status) {
        String tone = switch (status) {
            case ADDED, EXTRA -> "info";
            case MODIFIED, DIFFERENT -> "warning";
            case DUPLICATE -> "done";
            case DELETED, MISSING -> "danger";
            case SKIPPED -> "neutral";
        };
        return badge(status.label(), tone);
    }

    public static Label forScan(ScanStatus status) {
        String tone = switch (status) {
            case COMPLETED -> "success";
            case CANCELLED -> "neutral";
            case FAILED -> "danger";
        };
        return badge(status.label(), tone);
    }

    public static Label forLog(LogLevel level) {
        String tone = switch (level) {
            case INFO -> "info";
            case WARN -> "warning";
            case ERROR -> "danger";
        };
        return badge(level.name(), tone);
    }

    public static Label badge(String text, String tone) {
        Label label = new Label(text);
        label.getStyleClass().addAll("badge", "tone-" + tone);
        label.setMinWidth(Label.USE_PREF_SIZE);
        return label;
    }
}
