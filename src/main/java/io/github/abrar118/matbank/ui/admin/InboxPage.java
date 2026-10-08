package io.github.abrar118.matbank.ui.admin;

import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.domain.Feedback;
import io.github.abrar118.matbank.domain.SupportMessage;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Page;
import io.github.abrar118.matbank.ui.Ui;
import io.github.abrar118.matbank.ui.Workspace;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** Help-center messages and feedback for admins. */
public final class InboxPage implements Page {

    private final Workspace workspace;
    private final AppContext ctx;
    private final User admin;
    private final VBox reader = new VBox(14);

    public InboxPage(Workspace workspace) {
        this.workspace = workspace;
        this.ctx = workspace.context();
        this.admin = workspace.user();
    }

    @Override
    public Node view() {
        List<SupportMessage> messages = ctx.support().messages(admin);
        List<Feedback> feedback = ctx.support().feedback(admin);
        long unread = messages.stream().filter(m -> !m.read()).count();

        TabPane tabs = new TabPane(
                new Tab("Messages (" + unread + " unread)", messagesTab(messages)),
                new Tab("Feedback (" + feedback.size() + ")", feedbackTab(feedback)));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        return Ui.page(new VBox(20, new VBox(4, Ui.pageTitle("Inbox"),
                Ui.muted("Messages from clients and visitors, and star ratings.")), tabs));
    }

    private Node messagesTab(List<SupportMessage> messages) {
        LocalDate today = LocalDate.now(ctx.clock());
        ListView<SupportMessage> list = new ListView<>();
        list.getItems().setAll(messages);
        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(SupportMessage m, boolean empty) {
                super.updateItem(m, empty);
                if (empty || m == null) {
                    setGraphic(null);
                    return;
                }
                Label name = Ui.label(m.senderName(), m.read() ? Styles.TEXT_NORMAL : Styles.TEXT_BOLD);
                Label preview = Ui.label(m.body().lines().findFirst().orElse(""), Styles.TEXT_MUTED, Styles.TEXT_SMALL);
                preview.setMaxWidth(260);
                Label when = Ui.label(Formats.friendly(m.createdAt(), ctx.clock().getZone(), today), Styles.TEXT_MUTED,
                        Styles.TEXT_SMALL);
                Region dot = new Region();
                dot.getStyleClass().add(m.read() ? "read-dot" : "unread-dot");
                dot.setMinSize(8, 8);
                dot.setMaxSize(8, 8);
                VBox text = new VBox(2, new HBox(6, name, Ui.spacer(), dot), preview, when);
                HBox.setHgrow(text, Priority.ALWAYS);
                HBox row = new HBox(10, workspace.window().avatars().forName(m.senderName(), 34), text);
                row.setPadding(new Insets(4, 2, 4, 2));
                setGraphic(row);
            }
        });
        list.setPlaceholder(Ui.empty(Material2OutlinedMZ.MAIL, "No messages", "Messages from the help center land here."));
        list.getSelectionModel().selectedItemProperty().addListener((obs, o, m) -> open(m, list));
        list.setPrefWidth(360);
        list.setPrefHeight(520);

        reader.getChildren().setAll(Ui.empty(Material2OutlinedMZ.MAIL, "Select a message", "Pick a message to read it."));
        VBox readerCard = Ui.card(reader);
        HBox.setHgrow(readerCard, Priority.ALWAYS);
        HBox body = new HBox(20, list, readerCard);
        body.setPadding(new Insets(16, 0, 0, 0));
        return body;
    }

    private void open(SupportMessage m, ListView<SupportMessage> list) {
        if (m == null) {
            return;
        }
        if (!m.read()) {
            ctx.support().markRead(admin, m.id(), true);
            workspace.updateInboxBadge();
            int index = list.getItems().indexOf(m);
            // Swap in the read copy; this re-selects the row and calls open() again for the read version.
            list.getItems().set(index, new SupportMessage(m.id(), m.senderId(), m.senderName(), m.senderEmail(),
                    m.body(), true, m.createdAt()));
            list.getSelectionModel().select(index);
            return;
        }
        Label from = Ui.label(m.senderName(), Styles.TITLE_3);
        Label meta = Ui.muted((m.senderEmail() == null ? "No email given" : m.senderEmail())
                + "  ·  " + (m.senderId() == null ? "Visitor" : "Client") + "  ·  "
                + Formats.dateTime(m.createdAt(), ctx.clock().getZone()));
        Label text = Ui.wrapped(m.body(), "message-body");

        Button unread = Ui.secondary("Mark as unread", Material2OutlinedMZ.MARK_EMAIL_UNREAD);
        unread.setOnAction(e -> {
            ctx.support().markRead(admin, m.id(), false);
            workspace.navigate(Workspace.INBOX);
        });
        HBox actions = new HBox(10, unread);
        if (m.senderEmail() != null) {
            Button reply = Ui.primary("Reply by email", Material2OutlinedMZ.MAIL);
            reply.setOnAction(e -> Ui.open("mailto:" + m.senderEmail() + "?subject="
                    + URLEncoder.encode("Re: your message to MAT Bank", StandardCharsets.UTF_8).replace("+", "%20")));
            actions.getChildren().add(0, reply);
        }
        reader.getChildren().setAll(new HBox(14, workspace.window().avatars().forName(m.senderName(), 48),
                new VBox(4, from, meta)), text, actions);
        ((HBox) reader.getChildren().getFirst()).setAlignment(Pos.CENTER_LEFT);
    }

    private Node feedbackTab(List<Feedback> feedback) {
        VBox list = new VBox(12);
        double average = feedback.stream().mapToInt(Feedback::rating).average().orElse(0);
        HBox summary = new HBox(12, Ui.label(feedback.isEmpty() ? "-" : String.format("%.1f", average), "stat-value"),
                stars((int) Math.round(average), 22), Ui.muted("average from " + feedback.size() + " ratings"));
        summary.setAlignment(Pos.CENTER_LEFT);
        list.getChildren().add(Ui.card(summary));
        LocalDate today = LocalDate.now(ctx.clock());
        for (Feedback f : feedback) {
            HBox head = new HBox(10, workspace.window().avatars().forName(f.userName(), 32),
                    new VBox(1, Ui.label(f.userName(), Styles.TEXT_BOLD),
                            Ui.label(Formats.friendly(f.createdAt(), ctx.clock().getZone(), today), Styles.TEXT_MUTED,
                                    Styles.TEXT_SMALL)), Ui.spacer(), stars(f.rating(), 18));
            head.setAlignment(Pos.CENTER_LEFT);
            list.getChildren().add(Ui.card(head, Ui.wrapped(f.body())));
        }
        if (feedback.isEmpty()) {
            list.getChildren().add(Ui.empty(Material2OutlinedMZ.STAR_BORDER, "No feedback yet",
                    "Clients can rate the app from the help center."));
        }
        list.setPadding(new Insets(16, 0, 0, 0));
        return list;
    }

    private static Node stars(int rating, int size) {
        HBox box = new HBox(2);
        for (int i = 1; i <= 5; i++) {
            var star = Ui.icon(i <= rating ? Material2OutlinedMZ.STAR : Material2OutlinedMZ.STAR_BORDER, size);
            star.getStyleClass().add("rating-star");
            if (i <= rating) {
                star.getStyleClass().add("on");
            }
            box.getChildren().add(star);
        }
        return box;
    }
}
