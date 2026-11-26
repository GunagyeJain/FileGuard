package com.fileguard.ui.views;

import com.fileguard.AppInfo;
import com.fileguard.core.ReportExporter;
import com.fileguard.server.BackendServer;
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
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.nio.file.Path;

/** Theme switch, storage locations, backend information and details about the application. */
public class SettingsView extends StackPane {

    private final AppContext ctx;

    public SettingsView(AppContext ctx) {
        this.ctx = ctx;
        VBox page = new VBox(18,
                UiUtil.pageHeader("Settings", "Appearance, storage and information about FileGuard."),
                appearanceCard(), dataCard(), backendCard(), aboutCard());
        getChildren().add(UiUtil.scrollPage(page));
    }

    /** Segmented Light / Dark switch, kept in sync with the theme manager. */
    private VBox appearanceCard() {
        ToggleGroup group = new ToggleGroup();
        ToggleButton light = segment("Light", Icons.SUN, Theme.LIGHT, group, "segment-left");
        ToggleButton dark = segment("Dark", Icons.MOON, Theme.DARK, group, "segment-right");
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
        VBox text = new VBox(2, label("Theme", "setting-name"),
                label("Choose how FileGuard looks. Your choice is remembered.", "muted"));
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox row = new HBox(16, text, segmented);
        row.setAlignment(Pos.CENTER_LEFT);
        return UiUtil.card("Appearance", row);
    }

    private VBox dataCard() {
        Path dbFile = ctx.database().getFile().toAbsolutePath();
        Button openFolder = new Button("Open folder", Icons.create(Icons.FOLDER, 14));
        openFolder.setOnAction(e -> ctx.hostServices().showDocument(dbFile.getParent().toUri().toString()));
        return UiUtil.card("Data",
                label("Baselines and scan history are stored in a local SQLite database. "
                        + "It is created automatically and kept between sessions.", "muted"),
                readOnlyRow(dbFile.toString(), openFolder));
    }

    private VBox backendCard() {
        String url = ctx.backend().baseUri().toString();
        Button openViewer = new Button("Open log viewer");
        openViewer.setOnAction(e -> ctx.hostServices().showDocument(url));
        Label dbLabel = label("Log database: " + BackendServer.defaultDbFile().toAbsolutePath(), "muted");
        dbLabel.getStyleClass().add("small");
        return UiUtil.card("Local backend",
                label("A small REST server on this computer that records activity logs (scans, report exports, "
                        + "errors and settings changes) in its own SQLite database. FileGuard starts it automatically; "
                        + "it only accepts connections from this computer.", "muted"),
                readOnlyRow(url, openViewer), dbLabel);
    }

    private VBox aboutCard() {
        return UiUtil.card("About FileGuard",
                label("Version " + AppInfo.VERSION, "setting-name"),
                label("FileGuard checks file integrity, verifies backups and finds duplicate files using "
                        + "SHA-256 hashes. All processing happens locally on this computer.", "muted"),
                UiUtil.notice(ReportExporter.DISCLAIMER));
    }

    // ---------------------------------------------------------------- helpers

    private static ToggleButton segment(String text, String icon, Theme theme, ToggleGroup group, String side) {
        ToggleButton button = new ToggleButton(text, Icons.create(icon, 14));
        button.setToggleGroup(group);
        button.setUserData(theme);
        button.getStyleClass().addAll("segment", side);
        return button;
    }

    /** A read-only, monospace text field (for paths and addresses) with a button next to it. */
    private static HBox readOnlyRow(String value, Button action) {
        TextField field = new TextField(value);
        field.setEditable(false);
        field.getStyleClass().add("mono");
        HBox.setHgrow(field, Priority.ALWAYS);
        HBox row = new HBox(8, field, action);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Label label(String text, String style) {
        Label l = new Label(text);
        l.getStyleClass().add(style);
        l.setWrapText(true);
        l.setMinHeight(Region.USE_PREF_SIZE); // wrap instead of cutting the text off
        return l;
    }
}
