package com.fileguard.ui.views;

import com.fileguard.core.Format;
import com.fileguard.model.ScanReport;
import com.fileguard.model.ScanType;
import com.fileguard.ui.AppContext;
import com.fileguard.ui.Icons;
import com.fileguard.ui.Page;
import com.fileguard.ui.UiUtil;
import com.fileguard.ui.components.StatCard;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Start page: summary cards, quick actions and the most recent scans. */
public class DashboardView extends StackPane implements Refreshable {

    private static final int RECENT_LIMIT = 8;

    private final AppContext ctx;
    private final StatCard scansCard = new StatCard("Scans completed", Icons.CLOCK, "accent");
    private final StatCard baselinesCard = new StatCard("Saved baselines", Icons.SHIELD, "success");
    private final StatCard lastCheckCard = new StatCard("Latest integrity check", Icons.REFRESH, "warning");
    private final StatCard redundantCard = new StatCard("Redundant storage", Icons.COPY, "danger");
    private final TableView<ScanReport> recent = new TableView<>();

    public DashboardView(AppContext ctx, Consumer<Page> navigator) {
        this.ctx = ctx;

        HBox cards = new HBox(14, scansCard, baselinesCard, lastCheckCard, redundantCard);

        HBox actions = new HBox(14,
                actionTile(Icons.SHIELD, "Check file integrity",
                        "Save a baseline and detect added, modified or deleted files.", () -> navigator.accept(Page.INTEGRITY)),
                actionTile(Icons.BACKUP, "Verify a backup",
                        "Compare a folder with its backup by relative path and content.", () -> navigator.accept(Page.BACKUP)),
                actionTile(Icons.COPY, "Find duplicates",
                        "Locate identical files and see how much space they waste.", () -> navigator.accept(Page.DUPLICATES)));

        recent.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        recent.getColumns().add(ScanColumns.date());
        recent.getColumns().add(ScanColumns.type());
        recent.getColumns().add(ScanColumns.status());
        recent.getColumns().add(ScanColumns.summary());
        Label empty = new Label("No scans yet. Start with one of the quick actions above.");
        empty.getStyleClass().add("placeholder");
        recent.setPlaceholder(empty);
        recent.setMinHeight(200);
        recent.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && recent.getSelectionModel().getSelectedItem() != null) {
                navigator.accept(Page.HISTORY);
            }
        });
        VBox.setVgrow(recent, Priority.ALWAYS);

        Label viewAll = new Label("View full history →");
        viewAll.getStyleClass().add("link");
        viewAll.setCursor(Cursor.HAND);
        viewAll.setOnMouseClicked(e -> navigator.accept(Page.HISTORY));
        HBox recentHeader = new HBox(12, UiUtil.sectionTitle("Recent scans"), UiUtil.spacer(), viewAll);
        recentHeader.setAlignment(Pos.CENTER_LEFT);

        VBox page = new VBox(18,
                UiUtil.pageHeader("Dashboard", "An overview of your folders, baselines and recent scans."),
                cards,
                UiUtil.sectionTitle("Quick actions"), actions,
                recentHeader, recent,
                UiUtil.notice("FileGuard identifies differences using SHA-256 hashes. It cannot determine whether a change "
                        + "was intentional (an edit) or unwanted (damage), so always review the results yourself."));
        getChildren().add(UiUtil.scrollPage(page));
    }

    @Override
    public void refresh() {
        try {
            int completed = ctx.scanService().completedScanCount();
            List<ScanReport> latest = ctx.scanService().recentScans(RECENT_LIMIT);
            scansCard.setValue(String.valueOf(completed));
            scansCard.setCaption(latest.isEmpty() ? "No scans yet"
                    : "Last activity " + Format.dateTime(latest.get(0).startedAt()));

            int baselines = ctx.scanService().baselineCount();
            baselinesCard.setValue(String.valueOf(baselines));
            baselinesCard.setCaption(baselines == 1 ? "folder being monitored" : "folders being monitored");

            Optional<ScanReport> check = ctx.scanService().latestCompleted(ScanType.INTEGRITY);
            lastCheckCard.setValue(check.map(r -> Format.count(r.issuesFound(), "difference")).orElse("-"));
            lastCheckCard.setCaption(check.map(r -> Format.dateTime(r.startedAt())).orElse("No integrity check yet"));

            Optional<ScanReport> dup = ctx.scanService().latestCompleted(ScanType.DUPLICATES);
            redundantCard.setValue(dup.map(r -> Format.bytes(r.redundantBytes())).orElse("-"));
            redundantCard.setCaption(dup.map(r -> "Latest search, " + Format.dateTime(r.startedAt())).orElse("No duplicate search yet"));

            recent.setItems(FXCollections.observableArrayList(latest));
        } catch (Exception e) {
            UiUtil.showError(ctx, "Could not load dashboard", UiUtil.friendlyMessage(e));
        }
    }

    private VBox actionTile(String icon, String title, String description, Runnable onClick) {
        StackPane iconBox = new StackPane(Icons.create(icon, 18));
        iconBox.getStyleClass().addAll("stat-icon", "tone-accent");
        Label t = new Label(title);
        t.getStyleClass().add("tile-title");
        Label d = new Label(description);
        d.getStyleClass().add("muted");
        d.setWrapText(true);
        Label go = new Label("Open →");
        go.getStyleClass().add("link");
        VBox tile = new VBox(10, iconBox, t, d, go);
        tile.getStyleClass().addAll("card", "action-tile");
        tile.setCursor(Cursor.HAND);
        tile.setOnMouseClicked(e -> onClick.run());
        tile.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(tile, Priority.ALWAYS);
        return tile;
    }
}
