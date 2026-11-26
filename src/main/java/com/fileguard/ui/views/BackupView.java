package com.fileguard.ui.views;

import com.fileguard.core.ReportExporter;
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
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;

/** Page for comparing an original folder with its backup. */
public class BackupView extends StackPane {

    private final AppContext ctx;
    private final FolderField original = new FolderField("Original folder (the source)");
    private final FolderField backup = new FolderField("Backup folder (the copy)");
    private final Button verifyButton = new Button("Verify backup");
    private final Button exportButton = new Button("Export CSV");
    private final ScanProgressPanel progress = new ScanProgressPanel();
    private final StatCard examined = new StatCard("Files examined", Icons.STORAGE, "accent");
    private final StatCard missing = new StatCard("Missing in backup", Icons.WARNING, "danger");
    private final StatCard different = new StatCard("Content differs", Icons.EDIT, "warning");
    private final StatCard extra = new StatCard("Only in backup", Icons.BACKUP, "info");
    private final Label summary = new Label("No verification has been run in this session.");
    private final ResultsTable table = new ResultsTable(false,
            "Choose the original folder and its backup, then press Verify backup.");
    private ScanReport currentReport;

    public BackupView(AppContext ctx) {
        this.ctx = ctx;

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        Label l1 = new Label("Original");
        Label l2 = new Label("Backup");
        l1.getStyleClass().add("field-label");
        l2.getStyleClass().add("field-label");
        grid.addRow(0, l1, original);
        grid.addRow(1, l2, backup);
        GridPane.setHgrow(original, Priority.ALWAYS);
        GridPane.setHgrow(backup, Priority.ALWAYS);

        verifyButton.setGraphic(Icons.create(Icons.PLAY, 14));
        verifyButton.getStyleClass().add("button-primary");
        verifyButton.setOnAction(e -> verify());
        Label hint = new Label("Files are matched by their path relative to each folder, then compared by size and SHA-256 hash.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);
        HBox actions = new HBox(10, hint, UiUtil.spacer(), verifyButton);
        actions.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(hint, Priority.SOMETIMES);

        VBox folders = UiUtil.card("Folders to compare", grid, actions);

        exportButton.setGraphic(Icons.create(Icons.DOWNLOAD, 14));
        exportButton.setDisable(true);
        exportButton.setOnAction(e -> UiUtil.exportReport(ctx, getScene().getWindow(), currentReport));
        summary.getStyleClass().add("muted");
        HBox resultsHeader = new HBox(12, UiUtil.sectionTitle("Results"), summary, UiUtil.spacer(), exportButton);
        resultsHeader.setAlignment(Pos.CENTER_LEFT);
        table.setMinHeight(220);
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox page = new VBox(18,
                UiUtil.pageHeader("Backup verification",
                        "Check that a backup contains every file of the original folder with identical content."),
                folders, progress, UiUtil.metricStrip(examined, missing, different, extra),
                UiUtil.notice(ReportExporter.DISCLAIMER),
                resultsHeader, table);
        getChildren().add(UiUtil.scrollPage(page));
    }

    private void verify() {
        Path a = original.getPath();
        Path b = backup.getPath();
        if (a == null || b == null) {
            UiUtil.showInfo(ctx, "Folders missing", "Please choose both the original folder and the backup folder.");
            return;
        }
        UiUtil.runScan(ctx, progress, "Verifying backup",
                p -> ctx.scanService().verifyBackup(a, b, p),
                this::showReport,
                verifyButton, original, backup);
    }

    private void showReport(ScanReport report) {
        currentReport = report;
        exportButton.setDisable(false);
        summary.setText(report.summary());
        examined.setValue(String.valueOf(report.filesScanned()));
        examined.setCaption("in both folders");
        missing.setValue(String.valueOf(report.countOf(ResultStatus.MISSING)));
        different.setValue(String.valueOf(report.countOf(ResultStatus.DIFFERENT)));
        extra.setValue(String.valueOf(report.countOf(ResultStatus.EXTRA)));
        long skipped = report.countOf(ResultStatus.SKIPPED);
        extra.setCaption(skipped > 0 ? skipped + " unreadable item(s) skipped" : "");
        table.show(report.items(), "The backup matches the original: no missing or different files.");
    }
}
