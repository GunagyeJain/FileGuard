package com.fileguard.ui.components;

import com.fileguard.core.Format;
import com.fileguard.model.ResultItem;
import com.fileguard.model.ResultStatus;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;

import java.util.List;

/** Table that lists the rows of a scan result with coloured status badges. */
public class ResultsTable extends TableView<ResultItem> {

    private final Label placeholder = new Label();

    public ResultsTable(boolean showGroupColumn, String emptyText) {
        setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        placeholder.getStyleClass().add("placeholder");
        placeholder.setWrapText(true);
        placeholder.setText(emptyText);
        setPlaceholder(placeholder);

        TableColumn<ResultItem, ResultStatus> status = new TableColumn<>("Status");
        status.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().status()));
        status.setCellFactory(c -> new StatusCell());
        status.setPrefWidth(150);
        status.setMinWidth(130);
        getColumns().add(status);

        if (showGroupColumn) {
            TableColumn<ResultItem, Integer> group = new TableColumn<>("Group");
            group.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().group()));
            group.setCellFactory(c -> new TableCell<>() {
                @Override
                protected void updateItem(Integer item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null || item == 0 ? null : "#" + item);
                }
            });
            group.setPrefWidth(70);
            group.setMaxWidth(90);
            getColumns().add(group);
        }

        TableColumn<ResultItem, String> path = new TableColumn<>("Relative path");
        path.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().path()));
        path.setCellFactory(c -> new TooltipCell("mono"));
        path.setPrefWidth(360);

        TableColumn<ResultItem, Long> size = new TableColumn<>("Size");
        size.setCellValueFactory(c -> new ReadOnlyObjectWrapper<>(c.getValue().size()));
        size.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Long item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : Format.bytes(item));
            }
        });
        size.setPrefWidth(90);
        size.setMaxWidth(120);
        size.getStyleClass().add("align-right");

        TableColumn<ResultItem, String> detail = new TableColumn<>("Details");
        detail.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().detail()));
        detail.setCellFactory(c -> new TooltipCell("muted"));
        detail.setPrefWidth(320);

        getColumns().add(path);
        getColumns().add(size);
        getColumns().add(detail);
    }

    public void show(List<ResultItem> items, String emptyText) {
        placeholder.setText(emptyText);
        setItems(FXCollections.observableArrayList(items));
    }

    /** Cell showing the status as a small coloured pill. */
    static class StatusCell extends TableCell<ResultItem, ResultStatus> {
        @Override
        protected void updateItem(ResultStatus item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
            } else {
                setGraphic(StatusBadge.forResult(item));
            }
            setText(null);
        }
    }

    /** Text cell that shows the full value in a tooltip (long paths get cut off). */
    static class TooltipCell extends TableCell<ResultItem, String> {
        TooltipCell(String styleClass) {
            getStyleClass().add(styleClass);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty ? null : item);
            setTooltip(empty || item == null ? null : new Tooltip(item));
        }
    }
}
