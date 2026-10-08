package io.github.abrar118.matbank.ui.common;

import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.ui.Ui;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;

/** Wraps a page for visitors who aren't signed in: brand bar with a back button above the content. */
public final class StandalonePage {

    private final String title;
    private final Node content;
    private final Runnable back;

    public StandalonePage(String title, Node content, Runnable back) {
        this.title = title;
        this.content = content;
        this.back = back;
    }

    public Node build() {
        Button backButton = Ui.flat("Back", Material2OutlinedAL.ARROW_BACK);
        backButton.setOnAction(e -> back.run());
        HBox bar = new HBox(14, backButton, Ui.label(title, Styles.TITLE_3), Ui.spacer(),
                Ui.imageView("logo.png", 28), Ui.wordmark(16));
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("top-bar");
        bar.setPadding(new Insets(12, 24, 12, 16));
        BorderPane pane = new BorderPane(content);
        pane.setTop(bar);
        pane.getStyleClass().add("workspace");
        return pane;
    }
}
