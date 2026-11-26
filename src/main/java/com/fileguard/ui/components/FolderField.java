package com.fileguard.ui.components;

import com.fileguard.ui.Icons;
import javafx.beans.property.StringProperty;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** A text field plus a "Browse" button for choosing a folder. */
public class FolderField extends HBox {

    private final TextField field = new TextField();
    private final Button browse = new Button("Browse");

    public FolderField(String prompt) {
        super(8);
        setAlignment(Pos.CENTER_LEFT);
        field.setPromptText(prompt);
        field.getStyleClass().add("mono");
        HBox.setHgrow(field, Priority.ALWAYS);
        browse.setGraphic(Icons.create(Icons.FOLDER, 14));

        browse.setOnAction(e -> choose());
        getChildren().addAll(field, browse);
    }

    private void choose() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose folder");
        Path current = getPath();
        if (current != null && current.toFile().isDirectory()) {
            chooser.setInitialDirectory(current.toFile());
        }
        File dir = chooser.showDialog(getScene().getWindow());
        if (dir != null) {
            field.setText(dir.getAbsolutePath());
        }
    }

    /** @return the typed folder, or null if empty or not a valid path */
    public Path getPath() {
        String text = field.getText() == null ? "" : field.getText().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Path.of(text);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    public void setPath(String path) {
        field.setText(path);
    }

    public StringProperty textProperty() {
        return field.textProperty();
    }
}
