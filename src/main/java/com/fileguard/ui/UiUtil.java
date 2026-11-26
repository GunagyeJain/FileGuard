package com.fileguard.ui;

import com.fileguard.core.ReportExporter;
import com.fileguard.model.LogLevel;
import com.fileguard.model.ScanReport;
import com.fileguard.ui.components.ScanProgressPanel;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

/** Small helpers shared by the pages: layout pieces, dialogs, running scans and exporting. */
public final class UiUtil {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmm");

    private UiUtil() {
    }

    // ----------------------------------------------------------------- layout

    /** Page heading in the style of GitHub's "Subhead": title, description, divider. */
    public static HBox pageHeader(String title, String subtitle, Node... actions) {
        Label t = new Label(title);
        t.getStyleClass().add("page-title");
        Label s = new Label(subtitle);
        s.getStyleClass().add("page-subtitle");
        s.setWrapText(true);
        VBox text = new VBox(2, t, s);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox header = new HBox(8, text);
        header.getChildren().addAll(actions);
        header.setAlignment(Pos.BOTTOM_LEFT);
        header.getStyleClass().add("subhead");
        return header;
    }

    /** A bordered panel with a grey header row (GitHub "Box"). Heading may be null. */
    public static VBox card(String heading, Node... content) {
        return box(heading, null, content);
    }

    /** Same as {@link #card} but with controls on the right side of the header row. */
    public static VBox box(String heading, Node headerAction, Node... content) {
        VBox box = new VBox();
        box.getStyleClass().add("box");
        if (heading != null) {
            Label h = new Label(heading);
            h.getStyleClass().add("box-title");
            HBox header = new HBox(8, h, spacer());
            if (headerAction != null) {
                header.getChildren().add(headerAction);
            }
            header.setAlignment(Pos.CENTER_LEFT);
            header.getStyleClass().add("box-header");
            box.getChildren().add(header);
        }
        VBox body = new VBox(12, content);
        body.getStyleClass().add("box-body");
        box.getChildren().add(body);
        return box;
    }

    /** Several metric cells joined into one bordered strip. */
    public static HBox metricStrip(com.fileguard.ui.components.StatCard... cells) {
        HBox strip = new HBox(cells);
        strip.getStyleClass().add("metric-strip");
        if (cells.length > 0) {
            cells[0].getStyleClass().add("first");
        }
        return strip;
    }

    /** Neutral note, used e.g. for "differences are not corruption". */
    public static HBox notice(String text) {
        return flash(text, "flash");
    }

    private static HBox flash(String text, String styleClass) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(label, Priority.ALWAYS);
        HBox box = new HBox(10, Icons.create(Icons.INFO, 16), label);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add(styleClass);
        return box;
    }

    public static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("section-title");
        return label;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    /** Wraps a page so it scrolls on small windows but fills the space on large ones. */
    public static ScrollPane scrollPage(VBox content) {
        content.getStyleClass().add("page");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(true);
        scroll.getStyleClass().add("page-scroll");
        return scroll;
    }

    // ---------------------------------------------------------------- dialogs

    public static void showError(AppContext ctx, String header, String message) {
        show(ctx, Alert.AlertType.ERROR, header, message);
    }

    public static void showInfo(AppContext ctx, String header, String message) {
        show(ctx, Alert.AlertType.INFORMATION, header, message);
    }

    public static boolean confirm(AppContext ctx, String header, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        alert.setHeaderText(header);
        alert.setTitle("FileGuard");
        ctx.themeManager().style(alert.getDialogPane());
        return alert.showAndWait().filter(b -> b == ButtonType.OK).isPresent();
    }

    private static void show(AppContext ctx, Alert.AlertType type, String header, String message) {
        Alert alert = new Alert(type, message, ButtonType.OK);
        alert.setHeaderText(header);
        alert.setTitle("FileGuard");
        ctx.themeManager().style(alert.getDialogPane());
        alert.showAndWait();
    }

    /** Turns an exception into a message a normal user can understand. */
    public static String friendlyMessage(Throwable e) {
        if (e instanceof SQLException) {
            return "A database error occurred: " + e.getMessage();
        }
        if (e instanceof IOException || e instanceof IllegalArgumentException || e instanceof IllegalStateException) {
            return e.getMessage();
        }
        return e.getClass().getSimpleName() + (e.getMessage() != null ? ": " + e.getMessage() : "");
    }

    // ------------------------------------------------------------ background

    /** Work that may block (database, network) and therefore must not run on the UI thread. */
    @FunctionalInterface
    public interface BackgroundWork<T> {
        T run() throws Exception;
    }

    /**
     * Runs {@code work} on a background thread and then calls {@code onSuccess} or
     * {@code onError} back on the JavaFX thread, so pages never freeze while waiting.
     */
    public static <T> void background(BackgroundWork<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        javafx.concurrent.Task<T> task = new javafx.concurrent.Task<>() {
            @Override
            protected T call() throws Exception {
                return work.run();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> onError.accept(task.getException()));
        Thread thread = new Thread(task, "fileguard-background");
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * Starts a scan in the background, shows its progress in the panel and calls
     * {@code onSuccess} on the UI thread when it completes. The given controls are
     * disabled while the scan runs.
     */
    public static void runScan(AppContext ctx, ScanProgressPanel panel, String title, ScanTask.Work work,
                               Consumer<ScanReport> onSuccess, Node... controlsToDisable) {
        ScanTask task = new ScanTask(work);
        for (Node n : controlsToDisable) {
            n.setDisable(true);
        }
        Runnable enableControls = () -> {
            for (Node n : controlsToDisable) {
                n.setDisable(false);
            }
        };

        task.setOnSucceeded(e -> {
            enableControls.run();
            ScanReport report = task.getValue();
            panel.finish("done", "Scan complete", report.summary());
            onSuccess.accept(report);
        });
        task.setOnCancelled(e -> {
            enableControls.run();
            panel.finish("cancelled", "Scan cancelled",
                    "The scan was stopped before it finished. No results were kept for this run.");
        });
        task.setOnFailed(e -> {
            enableControls.run();
            String message = friendlyMessage(task.getException());
            panel.finish("failed", "Scan could not be completed", message);
            showError(ctx, "Scan could not be completed", message);
        });

        panel.track(task, title);
        task.start();
    }

    // ---------------------------------------------------------------- export

    public static void exportReport(AppContext ctx, Window owner, ScanReport report) {
        if (report == null) {
            showInfo(ctx, "Nothing to export", "Run a scan first, then export its results.");
            return;
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export report");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV file (*.csv)", "*.csv"));
        chooser.setInitialFileName("fileguard-" + report.type().name().toLowerCase() + "-"
                + report.startedAt().format(FILE_STAMP) + ".csv");
        File file = chooser.showSaveDialog(owner);
        if (file == null) {
            return;
        }
        try {
            ReportExporter.exportCsv(report, file.toPath());
            ctx.backend().log(LogLevel.INFO, "EXPORT", report.type().label() + " report exported ("
                    + report.items().size() + " rows)", file.getAbsolutePath());
            showInfo(ctx, "Report exported", "Saved " + report.items().size() + " rows to:\n" + file.getAbsolutePath());
        } catch (IOException ex) {
            ctx.backend().error("EXPORT", "Report export failed: " + ex.getMessage(), file.getAbsolutePath());
            showError(ctx, "Export failed", "Could not write the file: " + ex.getMessage());
        }
    }
}
