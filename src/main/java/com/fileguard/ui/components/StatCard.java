package com.fileguard.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * One cell of a metric strip: a small coloured dot with a label, a large value
 * and an optional caption. Several cells are placed side by side with
 * {@link com.fileguard.ui.UiUtil#metricStrip(StatCard...)}.
 *
 * tone: "accent", "success", "warning", "danger", "info", "done" or "neutral".
 */
public class StatCard extends VBox {

    private final Label value = new Label("—");
    private final Label caption = new Label();

    public StatCard(String title, String tone) {
        super(4);
        getStyleClass().add("metric");
        setMinWidth(140);
        setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(this, Priority.ALWAYS);

        Region dot = new Region();
        dot.getStyleClass().addAll("dot", "tone-" + tone);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("metric-title");
        HBox top = new HBox(8, dot, titleLabel);
        top.setAlignment(Pos.CENTER_LEFT);

        value.getStyleClass().add("metric-value");
        caption.getStyleClass().add("metric-caption");
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
        value.setText("—");
        caption.setText("");
    }
}
