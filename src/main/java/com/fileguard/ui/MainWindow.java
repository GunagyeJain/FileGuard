package com.fileguard.ui;

import com.fileguard.ui.views.BackupView;
import com.fileguard.ui.views.DashboardView;
import com.fileguard.ui.views.DuplicatesView;
import com.fileguard.ui.views.HistoryView;
import com.fileguard.ui.views.IntegrityView;
import com.fileguard.ui.views.Refreshable;
import com.fileguard.ui.views.SettingsView;
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

    private final Map<Page, Node> pages = new EnumMap<>(Page.class);
    private final Map<Page, ToggleButton> navButtons = new EnumMap<>(Page.class);
    private final ToggleGroup navGroup = new ToggleGroup();
    private final StackPane content = new StackPane();

    public MainWindow(AppContext ctx) {
        getStyleClass().add("main-window");

        pages.put(Page.DASHBOARD, new DashboardView(ctx, this::navigate));
        pages.put(Page.INTEGRITY, new IntegrityView(ctx));
        pages.put(Page.BACKUP, new BackupView(ctx));
        pages.put(Page.DUPLICATES, new DuplicatesView(ctx));
        pages.put(Page.HISTORY, new HistoryView(ctx));
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
        Node view = pages.get(page);
        content.getChildren().setAll(view);
        if (view instanceof Refreshable refreshable) {
            refreshable.refresh();
        }
    }

    private VBox buildSidebar() {
        // Brand
        StackPane mark = new StackPane(Icons.create(Icons.SHIELD, 18));
        mark.getStyleClass().add("brand-mark");
        Label name = new Label("FileGuard");
        name.getStyleClass().add("brand-name");
        Label tagline = new Label("Integrity toolkit");
        tagline.getStyleClass().add("brand-tagline");
        HBox brand = new HBox(10, mark, new VBox(0, name, tagline));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.getStyleClass().add("brand");

        Label tools = new Label("TOOLS");
        tools.getStyleClass().add("nav-section");

        VBox sidebar = new VBox(4, brand, navButton(Page.DASHBOARD), tools,
                navButton(Page.INTEGRITY), navButton(Page.BACKUP), navButton(Page.DUPLICATES),
                navButton(Page.HISTORY));
        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label footer = new Label("All data stays on this computer");
        footer.getStyleClass().add("sidebar-footer");
        footer.setWrapText(true);
        sidebar.getChildren().addAll(spacer, navButton(Page.SETTINGS), footer);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(230);
        sidebar.setMinWidth(230);
        return sidebar;
    }

    private ToggleButton navButton(Page page) {
        ToggleButton button = new ToggleButton(page.title(), Icons.create(page.icon(), 16));
        button.getStyleClass().add("nav-button");
        button.setToggleGroup(navGroup);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setGraphicTextGap(12);
        button.setOnAction(e -> navigate(page));
        navButtons.put(page, button);
        return button;
    }
}
