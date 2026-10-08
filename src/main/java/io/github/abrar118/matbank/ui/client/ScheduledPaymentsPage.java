package io.github.abrar118.matbank.ui.client;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.domain.Account;
import io.github.abrar118.matbank.domain.Frequency;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.RecurringPayment;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.RecurringPaymentService;
import io.github.abrar118.matbank.ui.Async;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Page;
import io.github.abrar118.matbank.ui.Ui;
import io.github.abrar118.matbank.ui.Workspace;
import io.github.abrar118.matbank.ui.components.Inputs;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.time.LocalDate;
import java.util.List;

/** Standing orders: rent, dues, allowances. They run automatically while the app is open or at next start. */
public final class ScheduledPaymentsPage implements Page {

    private final Workspace workspace;
    private final AppContext ctx;
    private final User user;

    public ScheduledPaymentsPage(Workspace workspace) {
        this.workspace = workspace;
        this.ctx = workspace.context();
        this.user = workspace.user();
    }

    @Override
    public Node view() {
        Button add = Ui.primary("New scheduled payment", Material2OutlinedAL.ADD);
        add.setOnAction(e -> openCreateDialog());
        HBox header = new HBox(new VBox(4, Ui.pageTitle("Scheduled payments"),
                Ui.muted("Pay the same person on a schedule. Missed runs are caught up when you next open the app.")),
                Ui.spacer(), add);
        header.setAlignment(Pos.CENTER_LEFT);

        List<RecurringPayment> payments = ctx.recurring().list(user.id());
        VBox list = new VBox(12);
        if (payments.isEmpty()) {
            list.getChildren().add(Ui.card(Ui.empty(Material2OutlinedMZ.REPEAT, "No scheduled payments",
                    "Set one up for rent, club dues or a monthly allowance.")));
        }
        for (RecurringPayment p : payments) {
            list.getChildren().add(row(p));
        }
        return Ui.page(new VBox(20, header, list));
    }

    private Node row(RecurringPayment p) {
        String recipientName = ctx.banking().findRecipient(p.recipientEmail()).map(User::fullName)
                .orElse(p.recipientEmail());
        StackPane badge = new StackPane(Ui.icon(Material2OutlinedMZ.REPEAT, 20));
        badge.getStyleClass().addAll("tx-badge", p.active() ? "out" : "internal");
        badge.setMinSize(44, 44);
        badge.setMaxSize(44, 44);

        VBox text = new VBox(3,
                Ui.label(p.note() == null ? "Payment to " + recipientName : p.note(), Styles.TITLE_4),
                Ui.label("To " + recipientName + " (" + p.recipientEmail() + ") from " + p.fromAccount().label(),
                        Styles.TEXT_MUTED),
                Ui.label(p.lastResult() == null ? "Not run yet" : p.lastResult(), Styles.TEXT_MUTED, Styles.TEXT_SMALL));
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);

        VBox when = new VBox(3, Ui.label(p.amount().format(), Styles.TITLE_4),
                Ui.label(p.frequency().label(), Styles.TEXT_MUTED),
                Ui.label(p.active() ? "Next: " + Formats.date(p.nextRun()) : "Paused", Styles.TEXT_SMALL));
        when.setAlignment(Pos.CENTER_RIGHT);
        when.setMinWidth(150);

        Button toggle = p.active()
                ? Ui.secondary("Pause", Material2OutlinedMZ.PAUSE)
                : Ui.secondary("Resume", Material2OutlinedMZ.PLAY_ARROW);
        toggle.setOnAction(e -> {
            try {
                if (p.active()) {
                    ctx.recurring().pause(user.id(), p.id());
                } else {
                    ctx.recurring().resume(user.id(), p.id());
                }
                workspace.navigate(Workspace.SCHEDULED);
            } catch (RuntimeException ex) {
                workspace.window().notifier().error(Async.describe(ex));
            }
        });
        Button delete = Ui.iconButton(Material2OutlinedAL.DELETE, "Delete");
        delete.setOnAction(e -> workspace.window().dialogs().confirm("Delete this scheduled payment?",
                "Future payments to " + recipientName + " will stop. Past payments are not affected.", "Delete", true,
                h -> {
                    ctx.recurring().delete(user.id(), p.id());
                    h.close();
                    workspace.navigate(Workspace.SCHEDULED);
                    workspace.window().notifier().success("Scheduled payment deleted.");
                }));

        HBox row = new HBox(16, badge, text, when, Ui.chip(p.active() ? "Active" : "Paused",
                p.active() ? Styles.SUCCESS : Styles.WARNING), toggle, delete);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("panel");
        row.setPadding(new Insets(16, 18, 16, 18));
        return row;
    }

    private void openCreateDialog() {
        List<Account> accounts = ctx.banking().accounts(user.id());
        ComboBox<Account> from = Inputs.accounts(accounts);
        TextField recipient = new TextField();
        recipient.setPromptText("recipient@example.com");
        CustomTextField amount = Inputs.money();
        TextField note = new TextField();
        note.setPromptText("e.g. Rent - Flat 4B");
        ComboBox<Frequency> frequency = new ComboBox<>();
        frequency.getItems().setAll(Frequency.values());
        frequency.setValue(Frequency.MONTHLY);
        frequency.setMaxWidth(Double.MAX_VALUE);
        DatePicker start = Inputs.date(LocalDate.now(ctx.clock()).plusDays(1));
        start.setMaxWidth(Double.MAX_VALUE);

        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(12);
        form.add(Ui.field("From", from), 0, 0, 2, 1);
        form.add(Ui.field("Recipient email", recipient), 0, 1, 2, 1);
        form.add(Ui.field("Amount", amount, "The 2% net transfer charge applies to each payment."), 0, 2);
        form.add(Ui.field("How often", frequency), 1, 2);
        form.add(Ui.field("First payment", start), 0, 3);
        form.add(Ui.field("Note", note), 1, 3);
        form.getColumnConstraints().addAll(half(), half());

        workspace.window().dialogs().show("New scheduled payment", form, 560, h -> {
            Button cancel = Ui.secondary("Cancel", null);
            cancel.setOnAction(e -> h.close());
            Button save = Ui.primary("Schedule", Material2OutlinedMZ.SCHEDULE);
            save.setDefaultButton(true);
            save.setOnAction(e -> {
                Money value = Inputs.amountOf(amount).orElse(null);
                if (value == null) {
                    h.error("Enter an amount.");
                    return;
                }
                var request = new RecurringPaymentService.NewPayment(from.getValue().type(), recipient.getText(),
                        value, note.getText(), frequency.getValue(), start.getValue());
                Async.run(save, () -> ctx.recurring().create(user.id(), request), created -> {
                    h.close();
                    workspace.window().notifier().success("Scheduled. First payment on "
                            + Formats.date(created.nextRun()) + ".");
                    // Pays immediately if the first run is today.
                    ctx.recurring().runDuePayments();
                    workspace.navigate(Workspace.SCHEDULED);
                }, h::error);
            });
            return new Node[]{cancel, save};
        });
    }

    private static javafx.scene.layout.ColumnConstraints half() {
        var c = new javafx.scene.layout.ColumnConstraints();
        c.setPercentWidth(50);
        return c;
    }
}
