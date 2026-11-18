package com.fileguard.ui.views;

import com.fileguard.core.Format;
import com.fileguard.model.ScanReport;
import com.fileguard.ui.AppContext;
import com.fileguard.ui.Icons;
import com.fileguard.ui.UiUtil;
import com.fileguard.ui.components.ResultsTable;
import javafx.collections.FXCollections;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Lists previous scans stored in SQLite and shows the results of the selected one. */
public class HistoryView extends StackPane implements Refreshable {

    private static final int HISTORY_LIMIT = 500;

    private final AppContext ctx;
    private final TableView<ScanReport> scans = new TableView<>();
    private final ResultsTable results = new ResultsTable(true, "Select a scan above to see its results.");
    private final Label detailTitle = new Label("Scan details");
    private final Label detailSummary = new Label("Select a scan above to see its results.");
    private final Button exportButton = new Button("Export CSV");
    private ScanReport selected;

    public HistoryView(AppContext ctx) {
        this.ctx = ctx;

        scans.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        scans.getColumns().add(ScanColumns.date());
        scans.getColumns().add(ScanColumns.type());
        scans.getColumns().add(ScanColumns.status());
        scans.getColumns().add(ScanColumns.files());
        scans.getColumns().add(ScanColumns.findings());
        scans.getColumns().add(ScanColumns.duration());
        scans.getColumns().add(ScanColumns.folder());
        Label empty = new Label("No scans yet. Results of every scan you run are saved here automatically.");
        empty.getStyleClass().add("placeholder");
        scans.setPlaceholder(empty);
        scans.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> showDetails(n));

        Button refreshButton = new Button("Refresh");
        refreshButton.setGraphic(Icons.create(Icons.REFRESH, 14));
        refreshButton.getStyleClass().add("button-secondary");
        refreshButton.setOnAction(e -> refresh());

        exportButton.setGraphic(Icons.create(Icons.DOWNLOAD, 14));
        exportButton.getStyleClass().add("button-secondary");
        exportButton.setDisable(true);
        exportButton.setOnAction(e -> UiUtil.exportReport(ctx, getScene().getWindow(), selected));

        detailTitle.getStyleClass().add("section-title");
        detailSummary.getStyleClass().add("muted");
        detailSummary.setWrapText(true);
        VBox detailText = new VBox(2, detailTitle, detailSummary);
        HBox.setHgrow(detailText, Priority.ALWAYS);
        HBox detailHeader = new HBox(12, detailText, exportButton);
        detailHeader.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(results, Priority.ALWAYS);
        VBox detailPane = new VBox(10, detailHeader, results);
        detailPane.getStyleClass().add("split-section");

        VBox scansPane = new VBox(scans);
        VBox.setVgrow(scans, Priority.ALWAYS);
        scansPane.getStyleClass().add("split-section");

        SplitPane split = new SplitPane(scansPane, detailPane);
        split.setOrientation(Orientation.VERTICAL);
        split.setDividerPositions(0.45);
        split.getStyleClass().add("history-split");
        VBox.setVgrow(split, Priority.ALWAYS);

        VBox page = new VBox(18,
                UiUtil.pageHeader("Scan history",
                        "Every scan is stored in the local SQLite database and remains available after restarting FileGuard.",
                        refreshButton),
                split);
        page.getStyleClass().add("page");
        getChildren().add(page);
    }

    @Override
    public void refresh() {
        long selectedId = selected == null ? -1 : selected.id();
        try {
            scans.setItems(FXCollections.observableArrayList(ctx.scanService().recentScans(HISTORY_LIMIT)));
            scans.getItems().stream().filter(s -> s.id() == selectedId).findFirst()
                    .ifPresent(s -> scans.getSelectionModel().select(s));
        } catch (Exception e) {
            UiUtil.showError(ctx, "Could not load history", UiUtil.friendlyMessage(e));
        }
    }

    private void showDetails(ScanReport scan) {
        if (scan == null) {
            selected = null;
            exportButton.setDisable(true);
            detailTitle.setText("Scan details");
            detailSummary.setText("Select a scan above to see its results.");
            results.show(java.util.List.of(), "Select a scan above to see its results.");
            return;
        }
        try {
            selected = ctx.scanService().loadScan(scan.id()).orElse(scan);
            exportButton.setDisable(false);
            detailTitle.setText(selected.type().label() + " · " + Format.dateTime(selected.startedAt()));
            detailSummary.setText(selected.summary());
            results.show(selected.items(), "This scan has no detailed results to show.");
        } catch (Exception e) {
            UiUtil.showError(ctx, "Could not load scan results", UiUtil.friendlyMessage(e));
        }
    }
}
