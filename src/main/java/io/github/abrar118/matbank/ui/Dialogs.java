package io.github.abrar118.matbank.ui;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.theme.Styles;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;

import java.util.function.Consumer;

/**
 * In-window modal dialogs drawn over a dimmed backdrop. The 2022 app opened a separate OS window for every
 * popup; these stay inside the main window and close with Esc.
 */
public final class Dialogs {

    /** Lets the dialog body close itself and show inline errors. */
    public final class Handle {
        private final Label error;

        private Handle(Label error) {
            this.error = error;
        }

        public void close() {
            modal.hide(true);
        }

        public void error(String message) {
            error.setText(message);
            error.setVisible(message != null && !message.isBlank());
            error.setManaged(error.isVisible());
        }
    }

    private final ModalPane modal;

    public Dialogs(ModalPane modal) {
        this.modal = modal;
    }

    public ModalPane modal() {
        return modal;
    }

    /**
     * Shows a dialog. {@code footer} receives the handle and returns the action buttons.
     */
    public Handle show(String title, Node body, double width, java.util.function.Function<Handle, Node[]> footer) {
        Label heading = Ui.label(title, Styles.TITLE_3);
        Button close = Ui.iconButton(Material2OutlinedAL.CLOSE, "Close");
        HBox header = new HBox(8, heading, Ui.spacer(), close);
        header.setAlignment(Pos.CENTER_LEFT);

        Label error = Ui.wrapped("", Styles.DANGER, "dialog-error");
        error.setVisible(false);
        error.setManaged(false);
        Handle handle = new Handle(error);

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().addAll(footer.apply(handle));

        VBox box = new VBox(16, header, body, error, actions);
        box.getStyleClass().addAll("panel", "dialog", Styles.ELEVATED_3);
        box.setPadding(new Insets(22, 24, 20, 24));
        box.setPrefWidth(width);
        box.setMaxWidth(width);
        box.setMaxHeight(Region.USE_PREF_SIZE);
        box.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.ESCAPE) {
                handle.close();
            }
        });
        close.setOnAction(e -> handle.close());

        modal.show(box);
        box.requestFocus();
        return handle;
    }

    public void info(String title, Node body) {
        show(title, body, 520, h -> {
            Button ok = Ui.primary("OK", null);
            ok.setDefaultButton(true);
            ok.setOnAction(e -> h.close());
            return new Node[]{ok};
        });
    }

    public void message(String title, String text) {
        info(title, Ui.wrapped(text));
    }

    /** Asks for confirmation; {@code onConfirm} runs with the handle so it can close or show an error. */
    public void confirm(String title, String text, String confirmLabel, boolean danger, Consumer<Handle> onConfirm) {
        show(title, Ui.wrapped(text), 460, h -> {
            Button cancel = Ui.secondary("Cancel", null);
            cancel.setCancelButton(true);
            cancel.setOnAction(e -> h.close());
            Button ok = Ui.primary(confirmLabel, null);
            if (danger) {
                ok.getStyleClass().remove(Styles.ACCENT);
                ok.getStyleClass().add(Styles.DANGER);
            }
            ok.setDefaultButton(true);
            ok.setOnAction(e -> onConfirm.accept(h));
            return new Node[]{cancel, ok};
        });
    }
}
