package io.github.abrar118.matbank.ui.common;

import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.SupportService;
import io.github.abrar118.matbank.ui.AppWindow;
import io.github.abrar118.matbank.ui.Async;
import io.github.abrar118.matbank.ui.Page;
import io.github.abrar118.matbank.ui.Ui;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.ArrayList;
import java.util.List;

/** The 2022 help center: call center details, a message to the admins and star-rated feedback. */
public final class HelpCenterPage implements Page {

    private final AppWindow window;
    private final AppContext ctx;
    private final User user;

    /** @param user the signed-in user, or {@code null} for a visitor */
    public HelpCenterPage(AppWindow window, User user) {
        this.window = window;
        this.ctx = window.context();
        this.user = user;
    }

    @Override
    public Node view() {
        VBox intro = new VBox(4, Ui.pageTitle("How can we help?"),
                Ui.muted("Message the MAT Bank team or tell us how we're doing."));
        VBox left = new VBox(20, messageCard(), callCenterCard());
        HBox.setHgrow(left, Priority.ALWAYS);
        left.setPrefWidth(560);
        VBox right = new VBox(20, feedbackCard());
        right.setPrefWidth(420);
        right.setMinWidth(380);
        HBox row = new HBox(20, left, right);
        row.setFillHeight(false);
        return Ui.page(new VBox(20, intro, row));
    }

    private Node callCenterCard() {
        StackPane icon = new StackPane(Ui.icon(Material2OutlinedMZ.SUPPORT_AGENT, 26));
        icon.getStyleClass().addAll("tx-badge", "in");
        icon.setMinSize(52, 52);
        icon.setMaxSize(52, 52);
        VBox text = new VBox(3, Ui.label("Call center", Styles.TITLE_4),
                Ui.label("+880 9600-000 2022  (demo number)", Styles.TEXT_BOLD),
                Ui.muted("Every day, 9 AM to 9 PM. Have your account number ready."));
        HBox row = new HBox(16, icon, text);
        row.setAlignment(Pos.CENTER_LEFT);
        return Ui.card(row);
    }

    private Node messageCard() {
        TextField name = new TextField();
        name.setPromptText("Your name");
        TextField email = new TextField();
        email.setPromptText("Email so we can reply (optional)");
        TextArea body = new TextArea();
        body.setPromptText("Write your message...");
        body.setWrapText(true);
        body.setPrefRowCount(6);

        Button send = Ui.primary("Send message", Material2OutlinedMZ.SEND);
        send.setOnAction(e -> Async.run(send, () -> {
            ctx.support().sendMessage(user, name.getText(), email.getText(), body.getText());
            return true;
        }, ok -> {
            body.clear();
            window.notifier().success("Message sent. An admin will get back to you soon.");
        }, window.notifier()::error));

        VBox card = Ui.card("Message the bank", null);
        if (user == null) {
            card.getChildren().add(new HBox(12, grow(Ui.field("Name", name)), grow(Ui.field("Email", email))));
        } else {
            card.getChildren().add(Ui.muted("Sending as " + user.fullName() + " (" + user.email() + ")"));
        }
        card.getChildren().addAll(Ui.field("Message", body,
                "Up to " + SupportService.MAX_MESSAGE_LENGTH + " characters."), send);
        return card;
    }

    private Node feedbackCard() {
        if (user == null) {
            return Ui.card("Share your thoughts", null, Ui.wrapped(
                    "Sign in to rate your experience. Your feedback helps us make the app better.", Styles.TEXT_MUTED));
        }
        int[] rating = {0};
        List<FontIcon> stars = new ArrayList<>();
        Label caption = Ui.muted("Tap a star");
        HBox starRow = new HBox(6);
        String[] words = {"", "Poor", "Fair", "Good", "Great", "Excellent"};
        for (int i = 1; i <= 5; i++) {
            int value = i;
            FontIcon star = Ui.icon(Material2OutlinedMZ.STAR_BORDER, 32);
            star.getStyleClass().add("rating-star");
            star.setCursor(Cursor.HAND);
            star.setOnMouseClicked(e -> {
                rating[0] = value;
                for (int s = 0; s < stars.size(); s++) {
                    stars.get(s).setIconCode(s < value ? Material2OutlinedMZ.STAR : Material2OutlinedMZ.STAR_BORDER);
                    stars.get(s).getStyleClass().removeAll("on");
                    if (s < value) {
                        stars.get(s).getStyleClass().add("on");
                    }
                }
                caption.setText(words[value]);
            });
            stars.add(star);
            starRow.getChildren().add(star);
        }
        starRow.getChildren().addAll(new javafx.scene.layout.Region(), caption);
        starRow.setAlignment(Pos.CENTER_LEFT);

        TextArea body = new TextArea();
        body.setPromptText("What do you like? What should we improve?");
        body.setWrapText(true);
        body.setPrefRowCount(6);
        Button submit = Ui.primary("Submit feedback", Material2OutlinedAL.FEEDBACK);
        submit.setOnAction(e -> Async.run(submit, () -> {
            ctx.support().submitFeedback(user, rating[0], body.getText());
            return true;
        }, ok -> {
            body.clear();
            window.notifier().success("Thank you for your feedback!");
        }, window.notifier()::error));

        return Ui.card("Share your thoughts", null,
                Ui.muted("Rate your experience with MAT Bank"), starRow, body, submit);
    }

    private static VBox grow(VBox box) {
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }
}
