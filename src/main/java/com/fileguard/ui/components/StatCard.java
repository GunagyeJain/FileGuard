package com.fileguard.ui.components;

import com.fileguard.ui.Icons;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * A summary card with an icon, a title, a large value and a small caption.
 * The accent ("accent", "success", "warning", "danger", "info") sets the icon colour.
 */
public class StatCard extends VBox {

    private final Label value = new Label("-");
    private final Label caption = new Label();

    public StatCard(String title, String icon, String accent) {
        super(10);
        getStyleClass().addAll("card", "stat-card");
        setMinWidth(150);
        HBox.setHgrow(this, Priority.ALWAYS);
        setMaxWidth(Double.MAX_VALUE);

        StackPane iconBox = new StackPane(Icons.create(icon, 16));
        iconBox.getStyleClass().addAll("stat-icon", "tone-" + accent);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("stat-title");
        HBox top = new HBox(10, iconBox, titleLabel);
        top.setAlignment(Pos.CENTER_LEFT);

        value.getStyleClass().add("stat-value");
        caption.getStyleClass().add("stat-caption");
        caption.setWrapText(true);
        getChildren().addAll(top, value, caption);
    }

    public void setValue(String text) {
        value.setText(text);
    }

    public void setCaption(String text) {
        caption.setText(text);
    }

    public void reset() {
        value.setText("-");
        caption.setText("");
    }
}
