package com.fileguard.ui.components;

import com.fileguard.ui.ScanTask;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Shows the progress of a running scan: a progress bar, the current file and
 * a Cancel button. When the scan ends it shows a short final status instead.
 */
public class ScanProgressPanel extends VBox {

    private final Label title = new Label();
    private final Label percent = new Label();
    private final Label message = new Label();
    private final ProgressBar bar = new ProgressBar(0);
    private final Button cancel = new Button("Cancel");
    private ScanTask task;

    public ScanProgressPanel() {
        super(8);
        getStyleClass().addAll("box", "progress-box");
        title.getStyleClass().add("progress-title");
        percent.getStyleClass().add("muted");
        message.getStyleClass().add("muted");
        message.setMaxWidth(Double.MAX_VALUE);
        bar.setMaxWidth(Double.MAX_VALUE);
        cancel.getStyleClass().addAll("button-danger", "button-small");
        cancel.setOnAction(e -> {
            if (task != null) {
                cancel.setDisable(true);
                message.textProperty().unbind();
                message.setText("Cancelling - finishing the current read safely...");
                // cancel(false): we do not interrupt the thread; the scan code checks
                // isCancelled() between reads and stops cleanly on its own.
                task.cancel(false);
            }
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox top = new HBox(10, title, spacer, percent, cancel);
        top.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(top, bar, message);

        setVisible(false);
        managedProperty().bind(visibleProperty());
    }

    /** Binds the panel to a task that is about to start. */
    public void track(ScanTask task, String titleText) {
        this.task = task;
        getStyleClass().removeAll("state-done", "state-cancelled", "state-failed");
        title.setText(titleText);
        bar.setVisible(true);
        bar.setManaged(true);
        cancel.setVisible(true);
        cancel.setDisable(false);
        bar.progressProperty().bind(task.progressProperty());
        message.textProperty().bind(task.messageProperty());
        percent.textProperty().bind(Bindings.createStringBinding(() -> {
            double p = task.getProgress();
            return p < 0 ? "" : Math.round(p * 100) + "%";
        }, task.progressProperty()));
        setVisible(true);
    }

    /** Replaces the progress bar with a final message. state: "done", "cancelled" or "failed". */
    public void finish(String state, String titleText, String detail) {
        bar.progressProperty().unbind();
        message.textProperty().unbind();
        percent.textProperty().unbind();
        task = null;
        percent.setText("");
        bar.setVisible(false);
        bar.setManaged(false);
        cancel.setVisible(false);
        title.setText(titleText);
        message.setText(detail);
        getStyleClass().add("state-" + state);
    }
}
