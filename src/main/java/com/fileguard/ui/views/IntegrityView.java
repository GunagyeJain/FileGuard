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
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;
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
    private final StatCard added = new StatCard("Added", "info");
    private final StatCard modified = new StatCard("Modified", "warning");
    private final StatCard deleted = new StatCard("Deleted", "danger");
    private final StatCard unchanged = new StatCard("Unchanged", "success");
    private final Label summary = new Label("No check has been run in this session.");
    private final ResultsTable table = new ResultsTable(false,
            "Choose a folder, save a baseline, and run an integrity check later to see what changed.");
    private ScanReport currentReport;

    public IntegrityView(AppContext ctx) {
        this.ctx = ctx;

        // Folder selection card
        savedBaselines.setPromptText("Saved baselines");
        savedBaselines.setPrefWidth(240);
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
        folder.textProperty().addListener((obs, o, n) -> updateBaselineStatus());

        createButton.setGraphic(Icons.create(Icons.SAVE, 14));
        createButton.getStyleClass().add("button-secondary");
        createButton.setOnAction(e -> createBaseline());
        checkButton.setGraphic(Icons.create(Icons.PLAY, 14));
        checkButton.getStyleClass().add("button-primary");
        checkButton.setOnAction(e -> runCheck());
        HBox actions = new HBox(10, baselineStatus, UiUtil.spacer(), createButton, checkButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(baselineStatus, Priority.SOMETIMES);

        VBox folderCard = UiUtil.card("Monitored folder", folderRow, actions);

        // Results
        HBox cards = UiUtil.metricStrip(added, modified, deleted, unchanged);
        exportButton.setGraphic(Icons.create(Icons.DOWNLOAD, 14));
        exportButton.getStyleClass().add("button-secondary");
        exportButton.setDisable(true);
        exportButton.setOnAction(e -> UiUtil.exportReport(ctx, getScene().getWindow(), currentReport));
        summary.getStyleClass().add("muted");
        HBox resultsHeader = new HBox(12, UiUtil.sectionTitle("Results"), summary, UiUtil.spacer(), exportButton);
        resultsHeader.setAlignment(Pos.CENTER_LEFT);
        table.setMinHeight(220);
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox page = new VBox(18,
                UiUtil.pageHeader("File integrity",
                        "Save a baseline of a folder's files, then check it later for added, modified and deleted files."),
                folderCard, progress, cards,
                UiUtil.notice(ReportExporter.DISCLAIMER + " A \"modified\" file simply has different content than when the baseline was saved."),
                resultsHeader, table);
        getChildren().add(UiUtil.scrollPage(page));
        updateBaselineStatus();
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

    private void updateBaselineStatus() {
        Path path = folder.getPath();
        if (path == null) {
            baselineStatus.setText("No folder selected.");
            return;
        }
        if (!path.toFile().isDirectory()) {
            baselineStatus.setText("This folder does not exist.");
            return;
        }
        try {
            Optional<BaselineInfo> b = ctx.scanService().findBaseline(path);
            baselineStatus.setText(b.map(info -> "Baseline saved " + Format.dateTime(info.createdAt()) + " · "
                            + Format.count(info.fileCount(), "file") + " · " + Format.bytes(info.totalBytes()))
                    .orElse("No baseline saved for this folder yet."));
        } catch (Exception e) {
            baselineStatus.setText("Could not read baseline: " + UiUtil.friendlyMessage(e));
        }
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
