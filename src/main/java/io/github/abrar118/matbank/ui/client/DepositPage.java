package io.github.abrar118.matbank.ui.client;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.domain.Account;
import io.github.abrar118.matbank.domain.LedgerEntry;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.TxKind;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.FeePolicy;
import io.github.abrar118.matbank.service.LedgerService;
import io.github.abrar118.matbank.ui.Async;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Page;
import io.github.abrar118.matbank.ui.Ui;
import io.github.abrar118.matbank.ui.Workspace;
import io.github.abrar118.matbank.ui.components.Inputs;
import io.github.abrar118.matbank.ui.components.LedgerRow;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** The 2022 three-step deposit (amount, reference, receipt) with the same 2% handling fee. */
public final class DepositPage implements Page {

    private static final String[] STEPS = {"Amount", "Reference", "Receipt"};
    private static final String[] SUGGESTIONS = {"Salary", "Cash deposit", "Freelance payment", "Gift", "Refund"};

    private final Workspace workspace;
    private final AppContext ctx;
    private final User user;
    private final StackPane stepBody = new StackPane();
    private final List<Node> stepIndicators = new ArrayList<>();

    private final ComboBox<Account> account;
    private final CustomTextField amount = Inputs.money();
    private final TextField reference = new TextField();

    public DepositPage(Workspace workspace) {
        this.workspace = workspace;
        this.ctx = workspace.context();
        this.user = workspace.user();
        this.account = Inputs.accounts(ctx.banking().accounts(user.id()));
    }

    @Override
    public Node view() {
        HBox progress = new HBox(0);
        progress.setAlignment(Pos.CENTER_LEFT);
        for (int i = 0; i < STEPS.length; i++) {
            Label number = Ui.label(String.valueOf(i + 1), "step-number");
            HBox step = new HBox(8, number, Ui.label(STEPS[i], Styles.TEXT_BOLD));
            step.setAlignment(Pos.CENTER_LEFT);
            step.getStyleClass().add("step");
            stepIndicators.add(step);
            progress.getChildren().add(step);
            if (i < STEPS.length - 1) {
                Region line = new Region();
                line.getStyleClass().add("step-line");
                line.setPrefWidth(70);
                line.setMaxHeight(2);
                progress.getChildren().add(line);
            }
        }

        VBox wizard = Ui.card(progress, stepBody);
        wizard.setPrefWidth(560);
        HBox.setHgrow(wizard, Priority.ALWAYS);
        showStep(0);

        VBox side = new VBox(16, recentDeposits(), feeNote());
        side.setPrefWidth(380);
        side.setMinWidth(340);

        VBox content = new VBox(20, new VBox(4, Ui.pageTitle("Deposit money"),
                Ui.muted("Add money to your checking or savings account.")), topAligned(new HBox(20, wizard, side)));
        return Ui.page(content);
    }

    private void showStep(int index) {
        for (int i = 0; i < stepIndicators.size(); i++) {
            Node step = stepIndicators.get(i);
            step.getStyleClass().removeAll("active", "done");
            if (i < index) {
                step.getStyleClass().add("done");
            } else if (i == index) {
                step.getStyleClass().add("active");
            }
        }
        stepBody.getChildren().setAll(switch (index) {
            case 0 -> amountStep();
            case 1 -> referenceStep();
            default -> receiptStep();
        });
    }

    private Node amountStep() {
        Button convert = Ui.flat("Convert from another currency", Material2OutlinedMZ.MONETIZATION_ON);
        convert.setOnAction(e -> workspace.navigate(Workspace.CURRENCY));
        Button next = Ui.primary("Next", Material2OutlinedAL.ARROW_FORWARD);
        next.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        next.setDefaultButton(true);
        next.setOnAction(e -> {
            Optional<Money> value = Inputs.amountOf(amount);
            if (value.isEmpty() || value.get().isLessThan(FeePolicy.MIN_AMOUNT)
                    || value.get().isGreaterThan(FeePolicy.MAX_AMOUNT)) {
                workspace.window().notifier().error("Enter an amount between " + FeePolicy.MIN_AMOUNT.format()
                        + " and " + FeePolicy.MAX_AMOUNT.format() + ".");
                return;
            }
            showStep(1);
        });
        HBox actions = new HBox(10, convert, Ui.spacer(), next);
        actions.setAlignment(Pos.CENTER_LEFT);
        return new VBox(16, Ui.field("Deposit to", account), Ui.field("Amount", amount), actions);
    }

    private Node referenceStep() {
        reference.setPromptText("e.g. Salary - Padma Software Ltd");
        FlowPane chips = new FlowPane(8, 8);
        for (String suggestion : SUGGESTIONS) {
            Button chip = Ui.secondary(suggestion, null);
            chip.getStyleClass().addAll(Styles.SMALL, Styles.ROUNDED);
            chip.setOnAction(e -> reference.setText(suggestion));
            chips.getChildren().add(chip);
        }
        Button back = Ui.flat("Back", Material2OutlinedAL.ARROW_BACK);
        back.setOnAction(e -> showStep(0));
        Button next = Ui.primary("Review", Material2OutlinedAL.ARROW_FORWARD);
        next.setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
        next.setDefaultButton(true);
        next.setOnAction(e -> {
            if (reference.getText() == null || reference.getText().isBlank()) {
                workspace.window().notifier().error("Add a reference so you'll recognise this deposit later.");
                return;
            }
            showStep(2);
        });
        HBox actions = new HBox(10, back, Ui.spacer(), next);
        return new VBox(16, Ui.field("Reference", reference, "Where is this money from? Shown on your statement."),
                chips, actions);
    }

    private Node receiptStep() {
        Money value = Inputs.amountOf(amount).orElse(Money.ZERO);
        FeePolicy.DepositQuote quote = ctx.banking().fees().deposit(value);
        GridPane grid = TransferPage.receiptGrid(
                new String[]{"Date", "Account", "Reference", "Deposit", "Handling fee (2%)"},
                new Label[]{Ui.label(Formats.date(LocalDate.now(ctx.clock()))),
                        Ui.label(account.getValue().type().label() + "  ·  " + account.getValue().maskedNumber()),
                        Ui.label(reference.getText().strip()),
                        Ui.label(quote.amount().format()),
                        Ui.label("-" + quote.fee().format())});
        HBox total = new HBox(Ui.label("Total credited", Styles.TEXT_BOLD), Ui.spacer(),
                Ui.label(quote.credited().format(), Styles.TITLE_3));
        total.setAlignment(Pos.CENTER_LEFT);
        total.getStyleClass().add("receipt-total");

        Button back = Ui.flat("Back", Material2OutlinedAL.ARROW_BACK);
        back.setOnAction(e -> showStep(1));
        Button confirm = Ui.primary("Confirm deposit", Material2OutlinedAL.CHECK_CIRCLE);
        confirm.setDefaultButton(true);
        confirm.setOnAction(e -> Async.run(confirm, () -> ctx.banking().deposit(user.id(),
                account.getValue().type(), value, reference.getText()), r -> {
            workspace.window().notifier().success("Deposited " + r.total().format() + " into "
                    + r.account().label() + ". Reference " + r.reference() + ".");
            workspace.navigate(Workspace.DEPOSIT);
        }, workspace.window().notifier()::error));
        HBox actions = new HBox(10, back, Ui.spacer(), confirm);
        VBox receipt = new VBox(14, grid, total);
        receipt.getStyleClass().add("receipt");
        return new VBox(18, receipt, actions);
    }

    private Node recentDeposits() {
        List<LedgerEntry> deposits = ctx.ledger().search(user.id(),
                new LedgerService.HistoryFilter(null, TxKind.Category.DEPOSIT, null, null, null));
        VBox list = new VBox(2);
        LocalDate today = LocalDate.now(ctx.clock());
        deposits.stream().limit(5).forEach(d -> list.getChildren().add(LedgerRow.of(d, ctx.clock().getZone(), today)));
        if (deposits.isEmpty()) {
            list.getChildren().add(Ui.muted("No deposits yet."));
        }
        return Ui.card("Recent deposits", null, list);
    }

    static HBox topAligned(HBox row) {
        row.setFillHeight(false);
        return row;
    }

    private Node feeNote() {
        return Ui.card("About fees", null, Ui.wrapped("A 2% handling fee is deducted from each deposit. Moving money "
                + "between your own accounts is always free.", Styles.TEXT_MUTED));
    }
}
