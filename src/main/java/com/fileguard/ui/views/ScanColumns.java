package com.fileguard.ui.views;

import com.fileguard.core.Format;
import com.fileguard.model.ScanReport;
import com.fileguard.model.ScanStatus;
import com.fileguard.ui.components.StatusBadge;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.Tooltip;

/** Column definitions for tables that list scans (Dashboard and History pages). */
final class ScanColumns {

    private ScanColumns() {
    }

    static TableColumn<ScanReport, String> date() {
        TableColumn<ScanReport, String> col = text("Date", 160, r -> Format.dateTime(r.startedAt()));
        col.setMinWidth(160);
        return col;
    }

    static TableColumn<ScanReport, String> type() {
        return text("Scan type", 150, r -> r.type().label());
    }

    static TableColumn<ScanReport, String> folder() {
        return text("Folder", 300, r -> r.secondFolder() == null ? r.folder() : r.folder() + "  →  " + r.secondFolder());
    }

    static TableColumn<ScanReport, String> files() {
        return text("Files", 80, r -> String.valueOf(r.filesScanned()));
    }

    static TableColumn<ScanReport, String> findings() {
        return text("Findings", 90, r -> String.valueOf(r.issuesFound()));
    }

    static TableColumn<ScanReport, String> duration() {
        return text("Duration", 90, r -> Format.duration(r.durationMs()));
    }

    static TableColumn<ScanReport, String> summary() {
        return text("Summary", 320, ScanReport::summary);
    }

    static TableColumn<ScanReport, ScanStatus> status() {
        TableColumn<ScanReport, ScanStatus> col = new TableColumn<>("Status");
        col.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().status()));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(ScanStatus item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : StatusBadge.forScan(item));
            }
        });
        col.setPrefWidth(120);
        col.setMinWidth(120); // room for the "Completed" label
        return col;
    }

    private static TableColumn<ScanReport, String> text(String title, double width,
                                                        java.util.function.Function<ScanReport, String> value) {
        TableColumn<ScanReport, String> col = new TableColumn<>(title);
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : item);
                setTooltip(empty || item == null ? null : new Tooltip(item));
            }
        });
        col.setPrefWidth(width);
        col.setMinWidth(Math.min(width, 140));
        return col;
    }
}
