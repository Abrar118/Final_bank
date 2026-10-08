package io.github.abrar118.matbank.ui;

import atlantafx.base.controls.Notification;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

/** Toast notifications stacked in the top-right corner. Replaces the 2022 one-second label flashes. */
public final class Notifier {

    public enum Kind {
        SUCCESS, INFO, ERROR
    }

    private final VBox stack = new VBox(10);

    public Notifier() {
        stack.setAlignment(Pos.TOP_RIGHT);
        stack.setPadding(new Insets(16));
        stack.setPickOnBounds(false);
        stack.setMaxWidth(420);
        StackPane.setAlignment(stack, Pos.TOP_RIGHT);
    }

    public VBox layer() {
        return stack;
    }

    public void success(String message) {
        show(message, Kind.SUCCESS);
    }

    public void info(String message) {
        show(message, Kind.INFO);
    }

    public void error(String message) {
        show(message, Kind.ERROR);
    }

    public Notification show(String message, Kind kind, Button... actions) {
        var icon = switch (kind) {
            case SUCCESS -> Ui.icon(Material2OutlinedAL.CHECK_CIRCLE, 20);
            case INFO -> Ui.icon(Material2OutlinedAL.INFO, 20);
            case ERROR -> Ui.icon(Material2OutlinedMZ.WARNING, 20);
        };
        Notification note = new Notification(message, icon);
        note.getStyleClass().addAll(Styles.ELEVATED_2, switch (kind) {
            case SUCCESS -> Styles.SUCCESS;
            case INFO -> Styles.ACCENT;
            case ERROR -> Styles.DANGER;
        });
        note.setPrefWidth(380);
        note.setMaxWidth(380);
        if (actions.length > 0) {
            note.setPrimaryActions(actions);
        }
        note.setOnClose(e -> dismiss(note));
        stack.getChildren().add(note);
        Animations.fadeInDown(note, Duration.millis(250)).playFromStart();

        PauseTransition timeout = new PauseTransition(Duration.seconds(kind == Kind.ERROR ? 7 : 4.5));
        timeout.setOnFinished(e -> dismiss(note));
        timeout.play();
        return note;
    }

    private void dismiss(Notification note) {
        if (!stack.getChildren().contains(note)) {
            return;
        }
        var out = Animations.fadeOut(note, Duration.millis(200));
        out.setOnFinished(e -> stack.getChildren().remove(note));
        out.playFromStart();
    }
}
