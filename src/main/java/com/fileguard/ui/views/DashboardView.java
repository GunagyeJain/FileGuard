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
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** Start page: key numbers, recent scans, shortcuts to the tools and the backend status. */
public class DashboardView extends StackPane implements Refreshable {

    private static final int RECENT_LIMIT = 10;

    private final AppContext ctx;
    private final StatCard scansCard = new StatCard("Scans completed", "accent");
    private final StatCard baselinesCard = new StatCard("Monitored folders", "success");
    private final StatCard lastCheckCard = new StatCard("Latest integrity check", "warning");
    private final StatCard redundantCard = new StatCard("Redundant storage", "done");
    private final TableView<ScanReport> recent = new TableView<>();
    private final Region backendDot = new Region();
    private final Label backendStatus = new Label("Checking…");
    private final Label backendLogs = new Label("—");

    public DashboardView(AppContext ctx, Consumer<Page> navigator) {
        this.ctx = ctx;

        // Recent scans (left column)
        recent.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        recent.getColumns().add(ScanColumns.date());
        recent.getColumns().add(ScanColumns.type());
        recent.getColumns().add(ScanColumns.status());
        recent.getColumns().add(ScanColumns.summary());
        Label empty = new Label("No scans yet. Pick a tool on the right to get started.");
        empty.getStyleClass().add("placeholder");
        recent.setPlaceholder(empty);
        recent.setMinHeight(260);
        recent.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && recent.getSelectionModel().getSelectedItem() != null) {
                navigator.accept(Page.HISTORY);
            }
        });
        VBox.setVgrow(recent, Priority.ALWAYS);

        Label viewAll = new Label("View all");
        viewAll.getStyleClass().addAll("link", "small");
        viewAll.setOnMouseClicked(e -> navigator.accept(Page.HISTORY));
        HBox recentHeader = new HBox(8, UiUtil.sectionTitle("Recent scans"), UiUtil.spacer(), viewAll);
        recentHeader.setAlignment(Pos.BASELINE_LEFT);
        VBox left = new VBox(10, recentHeader, recent);
        HBox.setHgrow(left, Priority.ALWAYS);
        VBox.setVgrow(left, Priority.ALWAYS);

        // Tools + backend (right column)
        VBox tools = new VBox(
                toolRow(Icons.SHIELD, "File integrity", "Detect added, modified and deleted files", () -> navigator.accept(Page.INTEGRITY), false),
                toolRow(Icons.BACKUP, "Backup verification", "Compare a folder with its backup", () -> navigator.accept(Page.BACKUP), false),
                toolRow(Icons.COPY, "Duplicate files", "Find identical files and wasted space", () -> navigator.accept(Page.DUPLICATES), true));
        tools.getStyleClass().add("box");
        Label toolsTitle = new Label("Tools");
        toolsTitle.getStyleClass().add("box-title");
        HBox toolsHeader = new HBox(toolsTitle);
        toolsHeader.getStyleClass().add("box-header");
        tools.getChildren().add(0, toolsHeader);

        backendDot.getStyleClass().add("status-dot");
        HBox statusRow = new HBox(8, backendDot, backendStatus);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        Label address = new Label(ctx.backend().baseUri().toString());
        address.getStyleClass().addAll("mono", "muted");
        backendLogs.getStyleClass().add("muted");
        Label openLogs = new Label("Open activity log");
        openLogs.getStyleClass().add("link");
        openLogs.setOnMouseClicked(e -> navigator.accept(Page.LOGS));
        VBox backendBox = UiUtil.box("Local backend", null, statusRow, address, backendLogs, openLogs);

        VBox right = new VBox(16, tools, backendBox);
        right.setMinWidth(320);
        right.setPrefWidth(340);
        right.setMaxWidth(360);

        HBox columns = new HBox(24, left, right);
        VBox.setVgrow(columns, Priority.ALWAYS);

        VBox page = new VBox(20,
                UiUtil.pageHeader("Dashboard", "Overview of monitored folders, recent scans and the local backend."),
                UiUtil.metricStrip(scansCard, baselinesCard, lastCheckCard, redundantCard),
                columns,
                UiUtil.notice("FileGuard identifies differences using SHA-256 hashes. It cannot tell whether a change "
                        + "was intentional (an edit) or unwanted (damage), so review the results yourself."));
        getChildren().add(UiUtil.scrollPage(page));

        ctx.backend().addStatusListener(online -> javafx.application.Platform.runLater(this::refreshBackend));
    }

    @Override
    public void refresh() {
        try {
            int completed = ctx.scanService().completedScanCount();
            List<ScanReport> latest = ctx.scanService().recentScans(RECENT_LIMIT);
            scansCard.setValue(String.valueOf(completed));
            scansCard.setCaption(latest.isEmpty() ? "No scans yet" : "Last activity " + Format.dateTime(latest.get(0).startedAt()));

            int baselines = ctx.scanService().baselineCount();
            baselinesCard.setValue(String.valueOf(baselines));
            baselinesCard.setCaption(baselines == 1 ? "folder with a saved baseline" : "folders with a saved baseline");

            Optional<ScanReport> check = ctx.scanService().latestCompleted(ScanType.INTEGRITY);
            lastCheckCard.setValue(check.map(r -> Format.count(r.issuesFound(), "difference")).orElse("—"));
            lastCheckCard.setCaption(check.map(r -> Format.dateTime(r.startedAt())).orElse("No integrity check yet"));

            Optional<ScanReport> dup = ctx.scanService().latestCompleted(ScanType.DUPLICATES);
            redundantCard.setValue(dup.map(r -> Format.bytes(r.redundantBytes())).orElse("—"));
            redundantCard.setCaption(dup.map(r -> "Latest search " + Format.dateTime(r.startedAt())).orElse("No duplicate search yet"));

            recent.setItems(FXCollections.observableArrayList(latest));
        } catch (Exception e) {
            UiUtil.showError(ctx, "Could not load dashboard", UiUtil.friendlyMessage(e));
        }
        refreshBackend();
    }

    /** Asks the backend for its log counts in the background, so the page never waits on the network. */
    private void refreshBackend() {
        Task<Map<String, Integer>> task = new Task<>() {
            @Override
            protected Map<String, Integer> call() throws Exception {
                return ctx.backend().fetchStats();
            }
        };
        task.setOnSucceeded(e -> {
            Map<String, Integer> s = task.getValue();
            setBackendOnline(true);
            backendLogs.setText(s.getOrDefault("total", 0) + " log entries · "
                    + s.getOrDefault("WARN", 0) + " warnings · " + s.getOrDefault("ERROR", 0) + " errors");
        });
        task.setOnFailed(e -> {
            setBackendOnline(false);
            backendLogs.setText("Logs are kept in a queue and sent when the backend is back.");
        });
        Thread t = new Thread(task, "dashboard-backend-stats");
        t.setDaemon(true);
        t.start();
    }

    private void setBackendOnline(boolean online) {
        backendDot.getStyleClass().removeAll("online", "offline");
        backendDot.getStyleClass().add(online ? "online" : "offline");
        backendStatus.setText(online ? "Online" : "Offline");
    }

    private HBox toolRow(String icon, String title, String description, Runnable open, boolean last) {
        Label t = new Label(title);
        t.getStyleClass().add("strong");
        Label d = new Label(description);
        d.getStyleClass().addAll("muted", "small");
        VBox text = new VBox(1, t, d);
        HBox.setHgrow(text, Priority.ALWAYS);
        Button go = new Button("Open");
        go.getStyleClass().add("button-small");
        go.setMinWidth(Region.USE_PREF_SIZE);
        t.setMinWidth(0);
        d.setMinWidth(0);
        text.setMinWidth(0);
        go.setOnAction(e -> open.run());
        HBox row = new HBox(12, Icons.create(icon, 16), text, go);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("box-row");
        if (last) {
            row.getStyleClass().add("last");
        }
        return row;
    }
}
