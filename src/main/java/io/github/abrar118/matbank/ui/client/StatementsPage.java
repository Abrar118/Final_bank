package io.github.abrar118.matbank.ui.client;

import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.domain.Account;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.StatementService;
import io.github.abrar118.matbank.ui.Async;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Notifier;
import io.github.abrar118.matbank.ui.Page;
import io.github.abrar118.matbank.ui.Ui;
import io.github.abrar118.matbank.ui.Workspace;
import io.github.abrar118.matbank.ui.components.Inputs;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/** Pick an account and a period, preview the totals and save a PDF statement. */
public final class StatementsPage implements Page {

    private enum Period {
        THIS_MONTH("This month"),
        LAST_MONTH("Last month"),
        LAST_3_MONTHS("Last 3 months"),
        THIS_YEAR("This year"),
        CUSTOM("Custom dates");

        private final String label;

        Period(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Workspace workspace;
    private final AppContext ctx;
    private final User user;
    private final VBox preview = new VBox(12);

    public StatementsPage(Workspace workspace) {
        this.workspace = workspace;
        this.ctx = workspace.context();
        this.user = workspace.user();
    }

    @Override
    public Node view() {
        List<Account> accounts = ctx.banking().accounts(user.id());
        ComboBox<Account> account = Inputs.accounts(accounts);
        ComboBox<Period> period = new ComboBox<>();
        period.getItems().setAll(Period.values());
        period.setValue(Period.THIS_MONTH);
        period.setMaxWidth(Double.MAX_VALUE);
        DatePicker from = Inputs.date(null);
        DatePicker to = Inputs.date(null);
        from.setMaxWidth(Double.MAX_VALUE);
        to.setMaxWidth(Double.MAX_VALUE);

        Runnable applyPeriod = () -> {
            LocalDate today = LocalDate.now(ctx.clock());
            YearMonth month = YearMonth.from(today);
            switch (period.getValue()) {
                case THIS_MONTH -> set(from, to, month.atDay(1), today);
                case LAST_MONTH -> set(from, to, month.minusMonths(1).atDay(1), month.minusMonths(1).atEndOfMonth());
                case LAST_3_MONTHS -> set(from, to, month.minusMonths(2).atDay(1), today);
                case THIS_YEAR -> set(from, to, today.withDayOfYear(1), today);
                case CUSTOM -> {
                }
            }
            boolean custom = period.getValue() == Period.CUSTOM;
            from.setDisable(!custom);
            to.setDisable(!custom);
        };
        period.valueProperty().addListener((obs, o, n) -> applyPeriod.run());
        applyPeriod.run();

        Runnable refresh = () -> renderPreview(account.getValue(), from.getValue(), to.getValue());
        account.valueProperty().addListener((obs, o, n) -> refresh.run());
        from.valueProperty().addListener((obs, o, n) -> refresh.run());
        to.valueProperty().addListener((obs, o, n) -> refresh.run());
        refresh.run();

        Button download = Ui.primary("Save as PDF", Material2OutlinedMZ.PICTURE_AS_PDF);
        download.setOnAction(e -> save(download, account.getValue(), from.getValue(), to.getValue()));

        VBox form = Ui.card("Statement", null, Ui.field("Account", account), Ui.field("Period", period),
                new HBox(12, grow(Ui.field("From", from)), grow(Ui.field("To", to))), download);
        form.setPrefWidth(420);
        form.setMinWidth(380);

        VBox previewCard = Ui.card("Preview", null, preview);
        HBox.setHgrow(previewCard, Priority.ALWAYS);

        return Ui.page(new VBox(20, new VBox(4, Ui.pageTitle("Statements"),
                Ui.muted("Download an official-looking PDF statement for any period.")),
                DepositPage.topAligned(new HBox(20, form, previewCard))));
    }

    private void renderPreview(Account account, LocalDate from, LocalDate to) {
        if (account == null || from == null || to == null) {
            preview.getChildren().setAll(Ui.muted("Choose a period."));
            return;
        }
        try {
            StatementService.Statement s = ctx.statements().build(user.id(), account.type(), from, to);
            GridPane tiles = new GridPane();
            tiles.setHgap(12);
            tiles.setVgap(12);
            tiles.add(Ui.stat(Material2OutlinedMZ.SCHEDULE, "Opening balance", s.opening().format(),
                    Formats.date(s.from())), 0, 0);
            tiles.add(Ui.stat(Material2OutlinedMZ.RECEIPT_LONG, "Closing balance", s.closing().format(),
                    Formats.date(s.to())), 1, 0);
            tiles.add(Ui.stat(Material2OutlinedMZ.TRENDING_UP, "Money in", s.moneyIn().format(), null), 0, 1);
            tiles.add(Ui.stat(Material2OutlinedMZ.TRENDING_DOWN, "Money out", s.moneyOut().format(), null), 1, 1);
            ColumnConstraints half = new ColumnConstraints();
            half.setPercentWidth(50);
            tiles.getColumnConstraints().setAll(half, half);
            preview.getChildren().setAll(tiles,
                    Ui.muted(s.entries().size() + " transactions on " + account.type().label() + " "
                            + account.number() + " between " + Formats.date(s.from()) + " and "
                            + Formats.date(s.to()) + "."));
        } catch (RuntimeException e) {
            preview.getChildren().setAll(Ui.label(Async.describe(e), Styles.DANGER));
        }
    }

    private void save(Button button, Account account, LocalDate from, LocalDate to) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save statement");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF document", "*.pdf"));
        chooser.setInitialFileName("MAT-Bank-" + account.type().label() + "-" + from + "-to-" + to + ".pdf");
        File home = new File(System.getProperty("user.home"));
        File downloads = new File(home, "Downloads");
        chooser.setInitialDirectory(downloads.isDirectory() ? downloads : home);
        File file = chooser.showSaveDialog(workspace.window().stage());
        if (file == null) {
            return;
        }
        Path path = file.toPath();
        Async.run(button, () -> {
            StatementService.Statement s = ctx.statements().build(user.id(), account.type(), from, to);
            ctx.statements().writePdf(s, path);
            return path;
        }, saved -> {
            Button open = Ui.flat("Open", Material2OutlinedMZ.OPEN_IN_NEW);
            open.setOnAction(e -> Ui.open(saved.toUri().toString()));
            workspace.window().notifier().show("Statement saved to " + saved.getFileName() + ".",
                    Notifier.Kind.SUCCESS, open);
        }, workspace.window().notifier()::error);
    }

    private static void set(DatePicker from, DatePicker to, LocalDate a, LocalDate b) {
        from.setValue(a);
        to.setValue(b);
    }

    private static VBox grow(VBox box) {
        HBox.setHgrow(box, Priority.ALWAYS);
        box.setAlignment(Pos.TOP_LEFT);
        return box;
    }
}
