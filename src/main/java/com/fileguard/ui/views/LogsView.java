package com.fileguard.ui.views;

import com.fileguard.core.Format;
import com.fileguard.model.LogEntry;
import com.fileguard.model.LogLevel;
import com.fileguard.ui.AppContext;
import com.fileguard.ui.Icons;
import com.fileguard.ui.UiUtil;
import com.fileguard.ui.components.StatCard;
import com.fileguard.ui.components.StatusBadge;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;

/**
 * Shows the activity log stored by the local backend. Everything on this page is
 * loaded over HTTP from the backend's REST API, in the background.
 */
public class LogsView extends StackPane implements Refreshable {

    private static final int LIMIT = 500;
    private static final String ALL_LEVELS = "All levels";

    private long refreshRequest;

    private final AppContext ctx;
    private final ComboBox<String> levelFilter = new ComboBox<>();
    private final TableView<LogEntry> table = new TableView<>();
    private final Label placeholder = new Label("Loading…");
    private final Label status = new Label();
    private final StatCard total = new StatCard("Total entries", "accent");
    private final StatCard info = new StatCard("Info", "info");
    private final StatCard warn = new StatCard("Warnings", "warning");
    private final StatCard error = new StatCard("Errors", "danger");

    public LogsView(AppContext ctx) {
        this.ctx = ctx;

        levelFilter.getItems().addAll(ALL_LEVELS, "INFO", "WARN", "ERROR");
        levelFilter.setValue(ALL_LEVELS);
        levelFilter.setOnAction(e -> refresh());

        Button refreshButton = new Button("Refresh", Icons.create(Icons.REFRESH, 14));
        refreshButton.setOnAction(e -> refresh());
        Button browserButton = new Button("Open in browser");
        browserButton.setOnAction(e -> ctx.hostServices().showDocument(ctx.backend().baseUri().toString()));
        Button clearButton = new Button("Clear log");
        clearButton.getStyleClass().add("button-danger");
        clearButton.setOnAction(e -> clear());
        status.getStyleClass().addAll("muted", "small");
        HBox toolbar = new HBox(8, levelFilter, refreshButton, status, UiUtil.spacer(), browserButton, clearButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        buildTable();
        VBox.setVgrow(table, Priority.ALWAYS);

        VBox page = new VBox(16,
                UiUtil.pageHeader("Activity log",
                        "Events recorded by the local FileGuard backend (REST API at " + ctx.backend().baseUri().resolve("/api/logs") + ")."),
                UiUtil.metricStrip(total, info, warn, error),
                toolbar, table);
        page.getStyleClass().add("page");
        getChildren().add(page);
    }

    private void buildTable() {
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        placeholder.getStyleClass().add("placeholder");
        placeholder.setWrapText(true);
        table.setPlaceholder(placeholder);

        TableColumn<LogEntry, String> time = new TableColumn<>("Time");
        time.setCellValueFactory(c -> new ReadOnlyStringWrapper(Format.dateTime(c.getValue().timestamp())));
        time.setPrefWidth(160);
        time.setMinWidth(160);
        time.setMaxWidth(180);

        TableColumn<LogEntry, LogLevel> level = new TableColumn<>("Level");
        level.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().level()));
        level.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(LogLevel item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : StatusBadge.forLog(item));
            }
        });
        level.setPrefWidth(90);
        level.setMaxWidth(100);

        TableColumn<LogEntry, String> category = textColumn("Category", 110, LogEntry::category, "mono");
        category.setMaxWidth(130);
        TableColumn<LogEntry, String> message = textColumn("Message", 420, LogEntry::message, null);
        TableColumn<LogEntry, String> details = textColumn("Details", 300, LogEntry::details, "muted");

        table.getColumns().add(time);
        table.getColumns().add(level);
        table.getColumns().add(category);
        table.getColumns().add(message);
        table.getColumns().add(details);
    }

    private static TableColumn<LogEntry, String> textColumn(String title, double width,
                                                            java.util.function.Function<LogEntry, String> value, String style) {
        TableColumn<LogEntry, String> col = new TableColumn<>(title);
        col.setCellValueFactory(c -> new ReadOnlyStringWrapper(value.apply(c.getValue())));
        col.setCellFactory(c -> {
            TableCell<LogEntry, String> cell = new TableCell<>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty ? null : item);
                    setTooltip(empty || item == null || item.isEmpty() ? null : new Tooltip(item));
                }
            };
            if (style != null) {
                cell.getStyleClass().add(style);
            }
            return cell;
        });
        col.setPrefWidth(width);
        return col;
    }

    /** What one refresh loads from the backend. */
    private record LogsData(List<LogEntry> logs, Map<String, Integer> stats) {
    }

    /** Loads logs and counts from the backend on a background thread. */
    @Override
    public void refresh() {
        LogLevel filter = ALL_LEVELS.equals(levelFilter.getValue()) ? null : LogLevel.valueOf(levelFilter.getValue());
        status.setText("Loading\u2026");
        long request = ++refreshRequest; // a slower, older answer must not overwrite a newer one
        UiUtil.background(() -> new LogsData(ctx.backend().fetchLogs(LIMIT, filter), ctx.backend().fetchStats()),
                data -> {
                    if (request == refreshRequest) {
                        showLogs(data);
                    }
                },
                failure -> {
                    if (request == refreshRequest) {
                        showOffline();
                    }
                });
    }

    private void showLogs(LogsData data) {
        placeholder.setText("No log entries yet. Run a scan and its events will appear here.");
        table.setItems(FXCollections.observableArrayList(data.logs()));
        total.setValue(String.valueOf(data.stats().getOrDefault("total", 0)));
        info.setValue(String.valueOf(data.stats().getOrDefault("INFO", 0)));
        warn.setValue(String.valueOf(data.stats().getOrDefault("WARN", 0)));
        error.setValue(String.valueOf(data.stats().getOrDefault("ERROR", 0)));
        int shown = data.logs().size();
        status.setText("Showing " + shown + (shown == LIMIT ? " most recent" : "") + " entries");
    }

    private void showOffline() {
        table.setItems(FXCollections.observableArrayList());
        placeholder.setText("The backend is not reachable at " + ctx.backend().baseUri()
                + ". New events are queued and will be sent when it is running again.");
        status.setText("Backend offline");
        total.reset();
        info.reset();
        warn.reset();
        error.reset();
    }

    private void clear() {
        if (!UiUtil.confirm(ctx, "Clear the activity log?",
                "This permanently deletes all log entries stored by the backend. Scan history is not affected.")) {
            return;
        }
        UiUtil.background(() -> ctx.backend().clearLogs(),
                deleted -> refresh(),
                failure -> UiUtil.showError(ctx, "Could not clear the log", UiUtil.friendlyMessage(failure)));
    }
}
