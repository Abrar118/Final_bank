package io.github.abrar118.matbank.ui.components;

import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;

import java.util.function.Function;

/** Table columns whose cells render a whole row object as a node. */
public final class Tables {

    private Tables() {
    }

    public static <T> TableColumn<T, T> column(String title, double width, Function<T, Node> render) {
        return column(title, width, render, Pos.CENTER_LEFT);
    }

    public static <T> TableColumn<T, T> column(String title, double width, Function<T, Node> render, Pos alignment) {
        TableColumn<T, T> column = new TableColumn<>(title);
        column.setCellValueFactory(v -> new SimpleObjectProperty<>(v.getValue()));
        column.setPrefWidth(width);
        column.setSortable(false);
        column.setCellFactory(c -> {
            TableCell<T, T> cell = new TableCell<>() {
                @Override
                protected void updateItem(T item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(null);
                    setGraphic(empty || item == null ? null : render.apply(item));
                }
            };
            cell.setAlignment(alignment);
            return cell;
        });
        return column;
    }
}
