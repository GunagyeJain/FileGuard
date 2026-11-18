package com.fileguard.ui;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.prefs.Preferences;

/**
 * Switches between the light and dark stylesheet and remembers the choice
 * using the Java Preferences API (no extra file needed).
 */
public class ThemeManager {

    public enum Theme { LIGHT, DARK }

    private static final String PREF_KEY = "theme";
    private static final String BASE_CSS = css("base.css");
    private static final String LIGHT_CSS = css("theme-light.css");
    private static final String DARK_CSS = css("theme-dark.css");

    private final Preferences prefs = Preferences.userNodeForPackage(ThemeManager.class);
    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>();
    private final List<Scene> scenes = new ArrayList<>();

    public ThemeManager() {
        Theme saved;
        try {
            saved = Theme.valueOf(prefs.get(PREF_KEY, Theme.LIGHT.name()));
        } catch (IllegalArgumentException e) {
            saved = Theme.LIGHT;
        }
        theme.set(saved);
        theme.addListener((obs, old, now) -> {
            prefs.put(PREF_KEY, now.name());
            scenes.forEach(this::applyTo);
        });
    }

    public ObjectProperty<Theme> themeProperty() {
        return theme;
    }

    public void register(Scene scene) {
        scenes.add(scene);
        applyTo(scene);
    }

    /** Dialogs have their own scene, so they need the stylesheets too. */
    public void style(DialogPane pane) {
        pane.getStylesheets().setAll(BASE_CSS, theme.get() == Theme.DARK ? DARK_CSS : LIGHT_CSS);
    }

    private void applyTo(Scene scene) {
        scene.getStylesheets().setAll(BASE_CSS, theme.get() == Theme.DARK ? DARK_CSS : LIGHT_CSS);
    }

    private static String css(String name) {
        return Objects.requireNonNull(ThemeManager.class.getResource("/com/fileguard/css/" + name),
                "Missing stylesheet " + name).toExternalForm();
    }
}
