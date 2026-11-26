package com.fileguard.ui;

import com.fileguard.ui.views.BackupView;
import com.fileguard.ui.views.DashboardView;
import com.fileguard.ui.views.DuplicatesView;
import com.fileguard.ui.views.HistoryView;
import com.fileguard.ui.views.IntegrityView;
import com.fileguard.ui.views.LogsView;
import com.fileguard.ui.views.Refreshable;
import com.fileguard.ui.views.SettingsView;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;

/**
 * The main layout: a sidebar with navigation on the left and the current page
 * on the right. Pages are created once and kept, so a running scan continues
 * (and keeps its results) while the user looks at another page.
 */
public class MainWindow extends BorderPane {

    private static final double SIDEBAR_WIDTH = 236;

    private final Map<Page, Node> pages = new EnumMap<>(Page.class);
    private final Map<Page, ToggleButton> navButtons = new EnumMap<>(Page.class);
    private final Map<Page, Region> indicators = new EnumMap<>(Page.class);
    private final ToggleGroup navGroup = new ToggleGroup();
    private final StackPane content = new StackPane();
    private final AppContext ctx;

    public MainWindow(AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("main-window");

        pages.put(Page.DASHBOARD, new DashboardView(ctx, this::navigate));
        pages.put(Page.INTEGRITY, new IntegrityView(ctx));
        pages.put(Page.BACKUP, new BackupView(ctx));
        pages.put(Page.DUPLICATES, new DuplicatesView(ctx));
        pages.put(Page.HISTORY, new HistoryView(ctx));
        pages.put(Page.LOGS, new LogsView(ctx));
        pages.put(Page.SETTINGS, new SettingsView(ctx));

        content.getStyleClass().add("content");
        setLeft(buildSidebar());
        setCenter(content);

        // Keep one navigation item selected at all times.
        navGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null && old != null) {
                navGroup.selectToggle(old);
            }
        });
        navigate(Page.DASHBOARD);
    }

    public void navigate(Page page) {
        navButtons.get(page).setSelected(true);
        indicators.forEach((p, bar) -> {
            bar.getStyleClass().remove("active");
            if (p == page) {
                bar.getStyleClass().add("active");
            }
        });
        Node view = pages.get(page);
        content.getChildren().setAll(view);
        if (view instanceof Refreshable refreshable) {
            refreshable.refresh();
        }
    }

    private VBox buildSidebar() {
        Label name = new Label("FileGuard");
        name.getStyleClass().add("brand-name");
        HBox brand = new HBox(10, Icons.create(Icons.SHIELD, 22), name);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("brand");

        VBox sidebar = new VBox(2, brand,
                navItem(Page.DASHBOARD),
                section("Tools"), navItem(Page.INTEGRITY), navItem(Page.BACKUP), navItem(Page.DUPLICATES),
                section("Records"), navItem(Page.HISTORY), navItem(Page.LOGS));
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        sidebar.getChildren().addAll(spacer, navItem(Page.SETTINGS), statusLine());
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(SIDEBAR_WIDTH);
        sidebar.setMinWidth(SIDEBAR_WIDTH);
        return sidebar;
    }

    private static Label section(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("nav-section");
        return label;
    }

    /** A navigation button with a thin indicator bar that shows on the selected page. */
    private HBox navItem(Page page) {
        ToggleButton button = new ToggleButton(page.title(), Icons.create(page.icon(), 16));
        button.getStyleClass().add("nav-button");
        button.setToggleGroup(navGroup);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setGraphicTextGap(10);
        button.setOnAction(e -> navigate(page));
        HBox.setHgrow(button, Priority.ALWAYS);
        navButtons.put(page, button);

        Region indicator = new Region();
        indicator.getStyleClass().add("nav-indicator");
        indicators.put(page, indicator);
        HBox item = new HBox(4, indicator, button);
        item.setAlignment(Pos.CENTER_LEFT);
        return item;
    }

    /** "● Backend online" at the bottom of the sidebar, kept up to date by the backend client. */
    private VBox statusLine() {
        Region dot = new Region();
        dot.getStyleClass().add("status-dot");
        Label text = new Label("Backend: checking…");
        text.getStyleClass().add("small");
        HBox row = new HBox(8, dot, text);
        row.setAlignment(Pos.CENTER_LEFT);
        Label address = new Label(ctx.backend().baseUri().getHost() + ":" + ctx.backend().baseUri().getPort());
        address.getStyleClass().addAll("small", "muted", "mono");

        Runnable update = () -> {
            boolean online = ctx.backend().isOnline();
            dot.getStyleClass().removeAll("online", "offline");
            dot.getStyleClass().add(online ? "online" : "offline");
            text.setText(online ? "Backend online" : "Backend offline");
        };
        update.run();
        ctx.backend().addStatusListener(online -> Platform.runLater(update));

        VBox box = new VBox(2, row, address);
        box.getStyleClass().add("status-line");
        VBox.setMargin(box, new Insets(10, 0, 0, 0));
        return box;
    }
}
