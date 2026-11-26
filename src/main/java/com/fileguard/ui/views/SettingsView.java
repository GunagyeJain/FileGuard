package com.fileguard.ui.views;

import com.fileguard.core.ReportExporter;
import com.fileguard.ui.AppContext;
import com.fileguard.ui.Icons;
import com.fileguard.ui.ThemeManager.Theme;
import com.fileguard.ui.UiUtil;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;

/** Theme switch, database location and information about the application. */
public class SettingsView extends StackPane {

    public SettingsView(AppContext ctx) {
        // Appearance: a segmented Light / Dark switch.
        ToggleGroup group = new ToggleGroup();
        ToggleButton light = new ToggleButton("Light");
        light.setGraphic(Icons.create(Icons.SUN, 14));
        ToggleButton dark = new ToggleButton("Dark");
        dark.setGraphic(Icons.create(Icons.MOON, 14));
        light.setToggleGroup(group);
        dark.setToggleGroup(group);
        light.getStyleClass().addAll("segment", "segment-left");
        dark.getStyleClass().addAll("segment", "segment-right");
        light.setUserData(Theme.LIGHT);
        dark.setUserData(Theme.DARK);
        group.selectToggle(ctx.themeManager().themeProperty().get() == Theme.DARK ? dark : light);
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null) {
                group.selectToggle(old); // always keep one option selected
            } else {
                ctx.themeManager().themeProperty().set((Theme) now.getUserData());
            }
        });
        // Keep the switch in sync if the theme is changed from anywhere else.
        ctx.themeManager().themeProperty().addListener((obs, old, now) ->
                group.selectToggle(now == Theme.DARK ? dark : light));
        HBox segmented = new HBox(light, dark);
        segmented.getStyleClass().add("segmented");

        VBox themeText = new VBox(2, label("Theme", "setting-name"),
                label("Choose how FileGuard looks. Your choice is remembered.", "muted"));
        HBox.setHgrow(themeText, Priority.ALWAYS);
        HBox themeRow = new HBox(16, themeText, segmented);
        themeRow.setAlignment(Pos.CENTER_LEFT);

        // Data: where the SQLite database lives.
        Path dbFile = ctx.database().getFile().toAbsolutePath();
        TextField dbPath = new TextField(dbFile.toString());
        dbPath.setEditable(false);
        HBox.setHgrow(dbPath, Priority.ALWAYS);
        Button openFolder = new Button("Open folder");
        openFolder.setGraphic(Icons.create(Icons.FOLDER, 14));
        openFolder.getStyleClass().add("button-secondary");
        openFolder.setOnAction(e -> ctx.hostServices().showDocument(dbFile.getParent().toUri().toString()));
        HBox dbRow = new HBox(8, dbPath, openFolder);
        dbRow.setAlignment(Pos.CENTER_LEFT);

        // Backend: where the activity log is stored and how to reach it.
        Path backendDb = com.fileguard.server.BackendServer.defaultDbFile().toAbsolutePath();
        TextField backendUrl = new TextField(ctx.backend().baseUri().toString());
        backendUrl.setEditable(false);
        backendUrl.getStyleClass().add("mono");
        HBox.setHgrow(backendUrl, Priority.ALWAYS);
        Button openViewer = new Button("Open log viewer");
        openViewer.setOnAction(e -> ctx.hostServices().showDocument(ctx.backend().baseUri().toString()));
        HBox backendRow = new HBox(8, backendUrl, openViewer);
        backendRow.setAlignment(Pos.CENTER_LEFT);
        Label backendDbLabel = label("Log database: " + backendDb, "muted");
        backendDbLabel.getStyleClass().add("small");
        dbPath.getStyleClass().add("mono");

        VBox page = new VBox(18,
                UiUtil.pageHeader("Settings", "Appearance, storage and information about FileGuard."),
                UiUtil.card("Appearance", themeRow),
                UiUtil.card("Data",
                        label("Baselines and scan history are stored in a local SQLite database. "
                                + "It is created automatically and kept between sessions.", "muted"),
                        dbRow),
                UiUtil.card("Local backend",
                        label("A small REST server on this computer that records activity logs (scans, exports, errors) "
                                + "in its own SQLite database. FileGuard starts it automatically; it only accepts "
                                + "connections from this computer.", "muted"),
                        backendRow, backendDbLabel),
                UiUtil.card("About FileGuard",
                        label("Version 1.1.0", "setting-name"),
                        label("FileGuard checks file integrity, verifies backups and finds duplicate files using "
                                + "SHA-256 hashes. All processing happens locally on this computer.", "muted"),
                        UiUtil.notice(ReportExporter.DISCLAIMER)));
        getChildren().add(UiUtil.scrollPage(page));
    }

    private static Label label(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().add(style);
        l.setWrapText(true);
        l.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE); // wrap instead of cutting the text off
        return l;
    }
}
