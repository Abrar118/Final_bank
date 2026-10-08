package io.github.abrar118.matbank.ui.client;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.domain.Account;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.BankingService;
import io.github.abrar118.matbank.service.FeePolicy;
import io.github.abrar118.matbank.service.Receipt;
import io.github.abrar118.matbank.ui.Async;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Page;
import io.github.abrar118.matbank.ui.Ui;
import io.github.abrar118.matbank.ui.Workspace;
import io.github.abrar118.matbank.ui.components.Inputs;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Send money to another client, or move it between your own accounts. */
public final class TransferPage implements Page {

    private final Workspace workspace;
    private final AppContext ctx;
    private final User user;
    private final StackPane body = new StackPane();

    public TransferPage(Workspace workspace) {
        this.workspace = workspace;
        this.ctx = workspace.context();
        this.user = workspace.user();
    }

    @Override
    public Node view() {
        ToggleButton toClient = new ToggleButton("To another client", Ui.icon(Material2OutlinedMZ.PEOPLE, 16));
        ToggleButton between = new ToggleButton("Between my accounts", Ui.icon(Material2OutlinedMZ.SWAP_HORIZ, 16));
        ToggleGroup group = new ToggleGroup();
        toClient.setToggleGroup(group);
        between.setToggleGroup(group);
        toClient.getStyleClass().add(Styles.LEFT_PILL);
        between.getStyleClass().add(Styles.RIGHT_PILL);
        group.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null) {
                old.setSelected(true);
            } else {
                body.getChildren().setAll(now == toClient ? toClientForm() : betweenForm());
            }
        });
        toClient.setSelected(true);

        VBox content = new VBox(20, new VBox(4, Ui.pageTitle("Send money"),
                Ui.muted("Transfers to other MAT Bank clients arrive instantly in their checking account.")),
                new HBox(toClient, between), body);
        return Ui.page(content);
    }

    private Node toClientForm() {
        List<Account> accounts = ctx.banking().accounts(user.id());
        ComboBox<Account> from = Inputs.accounts(accounts);
        CustomTextField recipient = new CustomTextField();
        recipient.setPromptText("recipient@example.com");
        recipient.setLeft(Ui.icon(Material2OutlinedMZ.MAIL, 16));
        HBox recipientInfo = new HBox(8);
        recipientInfo.setAlignment(Pos.CENTER_LEFT);
        recipientInfo.setMinHeight(32);
        CustomTextField amount = Inputs.money();
        TextField message = new TextField();
        message.setPromptText("What's it for? (optional)");
        Label counter = Ui.label("0 / " + BankingService.MAX_NOTE_LENGTH, Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        message.textProperty().addListener((obs, old, now) -> {
            if (now.length() > BankingService.MAX_NOTE_LENGTH) {
                message.setText(old);
            }
            counter.setText(message.getText().length() + " / " + BankingService.MAX_NOTE_LENGTH);
        });

        // Receipt preview, as on the 2022 transaction screen.
        Label rName = Ui.label("-", Styles.TEXT_BOLD);
        Label rAmount = Ui.label(Money.ZERO.format());
        Label rCharge = Ui.label(Money.ZERO.format());
        Label rDiscount = Ui.label(Money.ZERO.format());
        Label rTotal = Ui.label(Money.ZERO.format(), Styles.TITLE_3);
        Label rDate = Ui.label(Formats.date(LocalDate.now(ctx.clock())));
        GridPane receipt = receiptGrid(
                new String[]{"Recipient", "Amount", "Service charge (5%)", "Loyalty discount (3%)", "Date"},
                new Label[]{rName, rAmount, rCharge, rDiscount, rDate});
        HBox totalRow = new HBox(Ui.label("Total", Styles.TEXT_BOLD), Ui.spacer(), rTotal);
        totalRow.setAlignment(Pos.CENTER_LEFT);
        totalRow.getStyleClass().add("receipt-total");

        Runnable updatePreview = () -> {
            Optional<Money> value = Inputs.amountOf(amount);
            FeePolicy.TransferQuote q = ctx.banking().fees().transfer(value.orElse(Money.ZERO));
            rAmount.setText(q.amount().format());
            rCharge.setText(q.charge().format());
            rDiscount.setText("-" + q.discount().format());
            rTotal.setText(q.total().format());
        };
        amount.textProperty().addListener((obs, o, n) -> updatePreview.run());

        PauseTransition lookup = new PauseTransition(Duration.millis(350));
        lookup.setOnFinished(e -> {
            String email = recipient.getText() == null ? "" : recipient.getText().strip();
            if (email.isEmpty()) {
                recipientInfo.getChildren().clear();
                rName.setText("-");
                return;
            }
            Optional<User> found = email.equalsIgnoreCase(user.email()) ? Optional.empty()
                    : ctx.banking().findRecipient(email);
            if (found.isPresent()) {
                recipientInfo.getChildren().setAll(workspace.window().avatars().of(found.get(), 26),
                        Ui.label(found.get().fullName(), Styles.TEXT_BOLD),
                        Ui.icon(Material2OutlinedAL.CHECK_CIRCLE, 16));
                recipientInfo.getStyleClass().setAll("recipient-ok");
                rName.setText(found.get().fullName());
            } else {
                String why = email.equalsIgnoreCase(user.email()) ? "That's you. Use \"Between my accounts\"."
                        : "No MAT Bank client with this email yet.";
                recipientInfo.getChildren().setAll(Ui.label(why, Styles.TEXT_MUTED));
                recipientInfo.getStyleClass().setAll("recipient-missing");
                rName.setText("-");
            }
        });
        recipient.textProperty().addListener((obs, o, n) -> lookup.playFromStart());

        Button review = Ui.primary("Review transfer", Material2OutlinedMZ.SEND);
        review.setOnAction(e -> {
            Optional<Money> value = Inputs.amountOf(amount);
            if (value.isEmpty()) {
                workspace.window().notifier().error("Enter the amount to send.");
                return;
            }
            Account source = from.getValue();
            FeePolicy.TransferQuote q = ctx.banking().fees().transfer(value.get());
            String to = recipient.getText() == null ? "" : recipient.getText().strip();
            String who = rName.getText().equals("-") ? to : rName.getText() + " (" + to + ")";
            workspace.window().dialogs().confirm("Send " + q.amount().format() + "?",
                    "To " + who + " from your " + source.type().label() + " account.\n"
                            + "Service charge after discount: " + q.fee().format() + "\n"
                            + "Total leaving your account: " + q.total().format(),
                    "Send money", false, h -> Async.run(() -> ctx.banking().transfer(user.id(), source.type(), to,
                            value.get(), message.getText()), r -> {
                        h.close();
                        showReceipt("Money sent", r);
                        workspace.navigate(Workspace.TRANSFER);
                    }, h::error));
        });

        VBox form = Ui.card("Transfer details", null,
                Ui.field("From", from),
                Ui.field("Recipient email", recipient), recipientInfo,
                Ui.field("Amount", amount, "Between " + FeePolicy.MIN_AMOUNT.format() + " and "
                        + FeePolicy.MAX_AMOUNT.format() + " per transfer."),
                Ui.field("Message", message), counter, review);
        form.setPrefWidth(520);
        HBox.setHgrow(form, Priority.ALWAYS);

        VBox receiptCard = Ui.card("Receipt preview", null, receipt, totalRow,
                Ui.wrapped("The recipient gets the full amount. The charge is paid by you.",
                        Styles.TEXT_MUTED, Styles.TEXT_SMALL));
        receiptCard.getStyleClass().add("receipt");
        receiptCard.setPrefWidth(380);
        receiptCard.setMinWidth(340);
        return DepositPage.topAligned(new HBox(20, form, receiptCard));
    }

    private Node betweenForm() {
        List<Account> accounts = ctx.banking().accounts(user.id());
        ComboBox<Account> from = Inputs.accounts(accounts);
        Label to = Ui.label("", Styles.TEXT_BOLD);
        Runnable updateTo = () -> {
            Account source = from.getValue();
            Account destination = accounts.stream().filter(a -> a.type() == source.type().other()).findFirst()
                    .orElseThrow();
            to.setText(destination.type().label() + "  ·  " + destination.balance().format());
        };
        from.valueProperty().addListener((obs, o, n) -> updateTo.run());
        updateTo.run();
        CustomTextField amount = Inputs.money();

        Button move = Ui.primary("Move money", Material2OutlinedMZ.SWAP_HORIZ);
        move.setOnAction(e -> {
            Optional<Money> value = Inputs.amountOf(amount);
            if (value.isEmpty()) {
                workspace.window().notifier().error("Enter the amount to move.");
                return;
            }
            Async.run(move, () -> ctx.banking().moveBetweenAccounts(user.id(), from.getValue().type(), value.get()),
                    r -> {
                        workspace.window().notifier().success("Moved " + r.amount().format() + " to "
                                + r.counterparty() + ".");
                        workspace.navigate(Workspace.TRANSFER);
                    }, workspace.window().notifier()::error);
        });

        VBox form = Ui.card("Move between my accounts", null,
                Ui.field("From", from),
                Ui.field("To", to),
                Ui.field("Amount", amount, "Moving money between your own accounts is free."),
                move);
        form.setMaxWidth(520);
        return form;
    }

    private void showReceipt(String title, Receipt r) {
        GridPane grid = receiptGrid(
                new String[]{"Reference", "To", "Amount", "Charge", "Total debited", "New balance", "Time"},
                new Label[]{Ui.label(r.reference(), Styles.TEXT_BOLD), Ui.label(r.counterparty()),
                        Ui.label(r.amount().format()), Ui.label(r.fee().format()),
                        Ui.label(r.total().format(), Styles.TEXT_BOLD),
                        Ui.label(r.account().label() + "  ·  " + r.newBalance().format()),
                        Ui.label(Formats.dateTime(r.at(), ctx.clock().getZone()))});
        HBox done = new HBox(10, Ui.icon(Material2OutlinedAL.CHECK_CIRCLE, 28),
                Ui.label("Transfer complete", Styles.TITLE_4));
        done.setAlignment(Pos.CENTER_LEFT);
        done.getStyleClass().add("success-banner");
        workspace.window().dialogs().info(title, new VBox(16, done, grid));
    }

    static GridPane receiptGrid(String[] names, Label[] values) {
        GridPane grid = new GridPane();
        grid.setHgap(24);
        grid.setVgap(10);
        for (int i = 0; i < names.length; i++) {
            grid.add(Ui.label(names[i], Styles.TEXT_MUTED), 0, i);
            values[i].setMaxWidth(Double.MAX_VALUE);
            grid.add(values[i], 1, i);
        }
        return grid;
    }
}
