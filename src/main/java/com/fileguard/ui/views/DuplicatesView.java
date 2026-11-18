package com.fileguard.ui.views;

import com.fileguard.core.Format;
import com.fileguard.model.ResultItem;
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
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;

/** Page for finding identical files inside a folder. */
public class DuplicatesView extends StackPane {

    private final AppContext ctx;
    private final FolderField folder = new FolderField("Folder to search for duplicate files");
    private final Button findButton = new Button("Find duplicates");
    private final Button exportButton = new Button("Export CSV");
    private final ScanProgressPanel progress = new ScanProgressPanel();
    private final StatCard examined = new StatCard("Files examined", Icons.STORAGE, "accent");
    private final StatCard groups = new StatCard("Duplicate groups", Icons.COPY, "warning");
    private final StatCard copies = new StatCard("Redundant copies", Icons.COPY, "info");
    private final StatCard wasted = new StatCard("Redundant storage", Icons.STORAGE, "danger");
    private final Label summary = new Label("No search has been run in this session.");
    private final ResultsTable table = new ResultsTable(true,
            "Choose a folder and press Find duplicates. Files in the same group have identical content.");
    private ScanReport currentReport;

    public DuplicatesView(AppContext ctx) {
        this.ctx = ctx;

        findButton.setGraphic(Icons.create(Icons.PLAY, 14));
        findButton.getStyleClass().add("button-primary");
        findButton.setOnAction(e -> find());
        HBox row = new HBox(10, folder, findButton);
        HBox.setHgrow(folder, Priority.ALWAYS);
        row.setAlignment(Pos.CENTER_LEFT);
        Label hint = new Label("Only files with the same size are hashed, which keeps the search fast. Empty (0 byte) files are ignored.");
        hint.getStyleClass().add("muted");
        hint.setWrapText(true);

        exportButton.setGraphic(Icons.create(Icons.DOWNLOAD, 14));
        exportButton.getStyleClass().add("button-secondary");
        exportButton.setDisable(true);
        exportButton.setOnAction(e -> UiUtil.exportReport(ctx, getScene().getWindow(), currentReport));
        summary.getStyleClass().add("muted");
        HBox resultsHeader = new HBox(12, UiUtil.sectionTitle("Duplicate groups"), summary, UiUtil.spacer(), exportButton);
        resultsHeader.setAlignment(Pos.CENTER_LEFT);
        table.setMinHeight(240);
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox page = new VBox(18,
                UiUtil.pageHeader("Duplicate files",
                        "Find files with identical content (same SHA-256 hash) and see how much space the extra copies use."),
                UiUtil.card("Folder", row, hint), progress,
                new HBox(14, examined, groups, copies, wasted),
                resultsHeader, table);
        getChildren().add(UiUtil.scrollPage(page));
    }

    private void find() {
        Path path = folder.getPath();
        if (path == null) {
            UiUtil.showInfo(ctx, "No folder selected", "Please choose a folder first.");
            return;
        }
        UiUtil.runScan(ctx, progress, "Searching for duplicates",
                p -> ctx.scanService().findDuplicates(path, p),
                this::showReport,
                findButton, folder);
    }

    private void showReport(ScanReport report) {
        currentReport = report;
        exportButton.setDisable(false);
        summary.setText(report.summary());
        int groupCount = report.items().stream().mapToInt(ResultItem::group).max().orElse(0);
        long duplicateRows = report.countOf(ResultStatus.DUPLICATE);
        examined.setValue(String.valueOf(report.filesScanned()));
        groups.setValue(String.valueOf(groupCount));
        copies.setValue(String.valueOf(duplicateRows - groupCount));
        copies.setCaption("files beyond the first copy");
        wasted.setValue(Format.bytes(report.redundantBytes()));
        wasted.setCaption("could be freed by keeping one copy");
        table.show(report.items(), "No duplicate files were found in this folder.");
    }
}
