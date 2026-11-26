package com.fileguard.ui.views;

import com.fileguard.core.Format;
import com.fileguard.core.ReportExporter;
import com.fileguard.model.BaselineInfo;
import com.fileguard.model.ResultStatus;
import com.fileguard.model.ScanReport;
import com.fileguard.ui.AppContext;
import com.fileguard.ui.Icons;
import com.fileguard.ui.UiUtil;
import com.fileguard.ui.components.FolderField;
import com.fileguard.ui.components.ResultsTable;
import com.fileguard.ui.components.ScanProgressPanel;
import com.fileguard.ui.components.StatCard;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.Optional;

/** Page for creating baselines and checking folders against them. */
public class IntegrityView extends StackPane implements Refreshable {

    private final AppContext ctx;
    private final FolderField folder = new FolderField("Choose the folder you want to monitor");
    private final ComboBox<BaselineInfo> savedBaselines = new ComboBox<>();
    private final Label baselineStatus = new Label();
    private final Button createButton = new Button("Save baseline");
    private final Button checkButton = new Button("Run integrity check");
    private final Button exportButton = new Button("Export CSV");
    private final ScanProgressPanel progress = new ScanProgressPanel();
    private final StatCard added = new StatCard("Added", Icons.ADD, "info");
    private final StatCard modified = new StatCard("Modified", Icons.EDIT, "warning");
    private final StatCard deleted = new StatCard("Deleted", Icons.DELETE, "danger");
    private final StatCard unchanged = new StatCard("Unchanged", Icons.CHECK, "success");
    private final Label summary = new Label("No check has been run in this session.");
    private final ResultsTable table = new ResultsTable(false,
            "Choose a folder, save a baseline, and run an integrity check later to see what changed.");
    private static final double COMBO_WIDTH = 240;
    private static final double TABLE_MIN_HEIGHT = 220;

    private final PauseTransition statusDelay = new PauseTransition(Duration.millis(300));
    private long statusRequest;
    private ScanReport currentReport;

    public IntegrityView(AppContext ctx) {
        this.ctx = ctx;
        // Wait until the user stops typing for a moment before looking up the baseline.
        statusDelay.setOnFinished(e -> updateBaselineStatus());
        folder.textProperty().addListener((obs, o, n) -> statusDelay.playFromStart());

        table.setMinHeight(TABLE_MIN_HEIGHT);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox page = new VBox(18,
                UiUtil.pageHeader("File integrity",
                        "Save a baseline of a folder's files, then check it later for added, modified and deleted files."),
                buildFolderCard(), progress, UiUtil.metricStrip(added, modified, deleted, unchanged),
                UiUtil.notice(ReportExporter.DISCLAIMER + " A \"modified\" file simply has different content than when the baseline was saved."),
                buildResultsHeader(), table);
        getChildren().add(UiUtil.scrollPage(page));
        updateBaselineStatus();
    }

    private VBox buildFolderCard() {
        savedBaselines.setPromptText("Saved baselines");
        savedBaselines.setPrefWidth(COMBO_WIDTH);
        savedBaselines.setCellFactory(l -> new BaselineCell());
        savedBaselines.setButtonCell(new BaselineCell());
        savedBaselines.setOnAction(e -> {
            BaselineInfo b = savedBaselines.getValue();
            if (b != null) {
                folder.setPath(b.folderPath());
            }
        });
        HBox folderRow = new HBox(8, folder, savedBaselines);
        HBox.setHgrow(folder, Priority.ALWAYS);
        folderRow.setAlignment(Pos.CENTER_LEFT);

        baselineStatus.getStyleClass().add("muted");
        baselineStatus.setWrapText(true);
        createButton.setGraphic(Icons.create(Icons.SAVE, 14));
        createButton.setOnAction(e -> createBaseline());
        checkButton.setGraphic(Icons.create(Icons.PLAY, 14));
        checkButton.getStyleClass().add("button-primary");
        checkButton.setOnAction(e -> runCheck());
        HBox actions = new HBox(10, baselineStatus, UiUtil.spacer(), createButton, checkButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(baselineStatus, Priority.SOMETIMES);
        return UiUtil.card("Monitored folder", folderRow, actions);
    }

    private HBox buildResultsHeader() {
        exportButton.setGraphic(Icons.create(Icons.DOWNLOAD, 14));
        exportButton.setDisable(true);
        exportButton.setOnAction(e -> UiUtil.exportReport(ctx, getScene().getWindow(), currentReport));
        summary.getStyleClass().add("muted");
        HBox header = new HBox(12, UiUtil.sectionTitle("Results"), summary, UiUtil.spacer(), exportButton);
        header.setAlignment(Pos.CENTER_LEFT);
        return header;
    }

    @Override
    public void refresh() {
        try {
            BaselineInfo selected = savedBaselines.getValue();
            savedBaselines.getItems().setAll(ctx.scanService().listBaselines());
            savedBaselines.setValue(selected != null && savedBaselines.getItems().contains(selected) ? selected : null);
        } catch (Exception e) {
            savedBaselines.getItems().clear();
        }
        updateBaselineStatus();
    }

    /** Shows whether the chosen folder has a baseline. The lookup runs in the background. */
    private void updateBaselineStatus() {
        Path path = folder.getPath();
        if (path == null) {
            baselineStatus.setText("No folder selected.");
            return;
        }
        long request = ++statusRequest; // ignore answers to older lookups
        UiUtil.background(() -> describeBaseline(path),
                text -> {
                    if (request == statusRequest) {
                        baselineStatus.setText(text);
                    }
                },
                error -> {
                    if (request == statusRequest) {
                        baselineStatus.setText("Could not read baseline: " + UiUtil.friendlyMessage(error));
                    }
                });
    }

    private String describeBaseline(Path path) throws IOException, SQLException {
        if (!Files.isDirectory(path)) {
            return "This folder does not exist.";
        }
        return ctx.scanService().findBaseline(path)
                .map(info -> "Baseline saved " + Format.dateTime(info.createdAt()) + " \u00b7 "
                        + Format.count(info.fileCount(), "file") + " \u00b7 " + Format.bytes(info.totalBytes()))
                .orElse("No baseline saved for this folder yet.");
    }

    private void createBaseline() {
        Path path = folder.getPath();
        if (path == null) {
            UiUtil.showInfo(ctx, "No folder selected", "Please choose a folder first.");
            return;
        }
        try {
            Optional<BaselineInfo> existing = ctx.scanService().findBaseline(path);
            if (existing.isPresent() && !UiUtil.confirm(ctx, "Replace existing baseline?",
                    "A baseline from " + Format.dateTime(existing.get().createdAt())
                            + " already exists for this folder. Saving a new one replaces it.")) {
                return;
            }
        } catch (Exception e) {
            UiUtil.showError(ctx, "Folder problem", UiUtil.friendlyMessage(e));
            return;
        }
        UiUtil.runScan(ctx, progress, "Saving baseline",
                p -> ctx.scanService().createBaseline(path, p),
                report -> {
                    showReport(report, false);
                    refresh();
                },
                createButton, checkButton, folder);
    }

    private void runCheck() {
        Path path = folder.getPath();
        if (path == null) {
            UiUtil.showInfo(ctx, "No folder selected", "Please choose a folder first.");
            return;
        }
        UiUtil.runScan(ctx, progress, "Checking folder against baseline",
                p -> ctx.scanService().checkIntegrity(path, p),
                report -> showReport(report, true),
                createButton, checkButton, folder);
    }

    private void showReport(ScanReport report, boolean isCheck) {
        currentReport = report;
        exportButton.setDisable(false);
        summary.setText(report.summary());
        if (isCheck) {
            long a = report.countOf(ResultStatus.ADDED);
            long m = report.countOf(ResultStatus.MODIFIED);
            added.setValue(String.valueOf(a));
            modified.setValue(String.valueOf(m));
            deleted.setValue(String.valueOf(report.countOf(ResultStatus.DELETED)));
            unchanged.setValue(String.valueOf(Math.max(0, report.filesScanned() - a - m)));
            long skipped = report.countOf(ResultStatus.SKIPPED);
            unchanged.setCaption(skipped > 0 ? skipped + " unreadable item(s) skipped" : "Same content as baseline");
            table.show(report.items(), "No differences found. All files match the baseline.");
        } else {
            added.reset();
            modified.reset();
            deleted.reset();
            unchanged.reset();
            table.show(report.items(), "Baseline saved. Run an integrity check later to detect changes.");
        }
    }

    /** Shows only the folder path of a baseline in the combo box. */
    private static class BaselineCell extends ListCell<BaselineInfo> {
        @Override
        protected void updateItem(BaselineInfo item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? "Saved baselines" : item.folderPath());
        }
    }
}
