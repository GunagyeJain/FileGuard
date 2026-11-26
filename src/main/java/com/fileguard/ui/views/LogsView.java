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
import javafx.concurrent.Task;
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

        levelFilter.getItems().addAll("All levels", "INFO", "WARN", "ERROR");
        levelFilter.setValue("All levels");
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

    /** Loads logs and counts from the backend on a background thread. */
    @Override
    public void refresh() {
        LogLevel filter = "All levels".equals(levelFilter.getValue()) ? null : LogLevel.valueOf(levelFilter.getValue());
        status.setText("Loading…");
        Task<Object[]> task = new Task<>() {
            @Override
            protected Object[] call() throws Exception {
                return new Object[]{ctx.backend().fetchLogs(LIMIT, filter), ctx.backend().fetchStats()};
            }
        };
        task.setOnSucceeded(e -> {
            @SuppressWarnings("unchecked")
            List<LogEntry> logs = (List<LogEntry>) task.getValue()[0];
            @SuppressWarnings("unchecked")
            Map<String, Integer> stats = (Map<String, Integer>) task.getValue()[1];
            placeholder.setText("No log entries yet. Run a scan and its events will appear here.");
            table.setItems(FXCollections.observableArrayList(logs));
            total.setValue(String.valueOf(stats.getOrDefault("total", 0)));
            info.setValue(String.valueOf(stats.getOrDefault("INFO", 0)));
            warn.setValue(String.valueOf(stats.getOrDefault("WARN", 0)));
            error.setValue(String.valueOf(stats.getOrDefault("ERROR", 0)));
            status.setText("Showing " + logs.size() + (logs.size() == LIMIT ? " most recent" : "") + " entries");
        });
        task.setOnFailed(e -> {
            table.setItems(FXCollections.observableArrayList());
            placeholder.setText("The backend is not reachable at " + ctx.backend().baseUri()
                    + ". New events are queued and will be sent when it is running again.");
            status.setText("Backend offline");
            total.reset();
            info.reset();
            warn.reset();
            error.reset();
        });
        start(task);
    }

    private void clear() {
        if (!UiUtil.confirm(ctx, "Clear the activity log?",
                "This permanently deletes all log entries stored by the backend. Scan history is not affected.")) {
            return;
        }
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return ctx.backend().clearLogs();
            }
        };
        task.setOnSucceeded(e -> refresh());
        task.setOnFailed(e -> UiUtil.showError(ctx, "Could not clear the log", UiUtil.friendlyMessage(task.getException())));
        start(task);
    }

    private static void start(Task<?> task) {
        Thread t = new Thread(task, "fileguard-logs");
        t.setDaemon(true);
        t.start();
    }
}
