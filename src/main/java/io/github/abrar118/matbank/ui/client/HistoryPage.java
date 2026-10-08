package io.github.abrar118.matbank.ui.client;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.theme.Styles;
import atlantafx.base.theme.Tweaks;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.domain.AccountType;
import io.github.abrar118.matbank.domain.LedgerEntry;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.TxKind;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.LedgerService;
import io.github.abrar118.matbank.ui.Async;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Page;
import io.github.abrar118.matbank.ui.Ui;
import io.github.abrar118.matbank.ui.Workspace;
import io.github.abrar118.matbank.ui.components.Inputs;
import io.github.abrar118.matbank.ui.components.LedgerRow;
import io.github.abrar118.matbank.ui.components.Tables;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.time.ZoneId;
import java.util.List;

/** Searchable, filterable transaction history. */
public final class HistoryPage implements Page {

    private static final String ALL_ACCOUNTS = "All accounts";
    private static final String ALL_TYPES = "All types";

    private final Workspace workspace;
    private final AppContext ctx;
    private final User user;
    private final String initialSearch;
    private final ZoneId zone;

    private final CustomTextField search = new CustomTextField();
    private final ComboBox<Object> account = new ComboBox<>();
    private final ComboBox<Object> category = new ComboBox<>();
    private final DatePicker from = Inputs.date(null);
    private final DatePicker to = Inputs.date(null);
    private final TableView<LedgerEntry> table = new TableView<>();
    private final Label summary = Ui.muted("");
    private int generation;

    public HistoryPage(Workspace workspace, String initialSearch) {
        this.workspace = workspace;
        this.ctx = workspace.context();
        this.user = workspace.user();
        this.initialSearch = initialSearch;
        this.zone = ctx.clock().getZone();
    }

    @Override
    public Node view() {
        search.setPromptText("Search name, email, note or reference");
        search.setLeft(Ui.icon(Material2OutlinedMZ.SEARCH, 16));
        search.setPrefWidth(300);
        if (initialSearch != null) {
            search.setText(initialSearch);
        }
        account.getItems().addAll(ALL_ACCOUNTS, AccountType.CHECKING, AccountType.SAVINGS);
        account.getSelectionModel().selectFirst();
        category.getItems().add(ALL_TYPES);
        category.getItems().addAll((Object[]) TxKind.Category.values());
        category.getSelectionModel().selectFirst();
        from.setPromptText("From");
        to.setPromptText("To");
        from.setPrefWidth(150);
        to.setPrefWidth(150);

        Button clear = Ui.flat("Clear", Material2OutlinedAL.CLEAR);
        clear.setOnAction(e -> {
            search.clear();
            account.getSelectionModel().selectFirst();
            category.getSelectionModel().selectFirst();
            from.setValue(null);
            to.setValue(null);
        });
        Button export = Ui.secondary("Export statement", Material2OutlinedMZ.PICTURE_AS_PDF);
        export.setOnAction(e -> workspace.navigate(Workspace.STATEMENTS));

        PauseTransition debounce = new PauseTransition(Duration.millis(250));
        debounce.setOnFinished(e -> reload());
        search.textProperty().addListener((obs, o, n) -> debounce.playFromStart());
        account.valueProperty().addListener((obs, o, n) -> reload());
        category.valueProperty().addListener((obs, o, n) -> reload());
        from.valueProperty().addListener((obs, o, n) -> reload());
        to.valueProperty().addListener((obs, o, n) -> reload());

        HBox.setHgrow(search, javafx.scene.layout.Priority.ALWAYS);
        search.setMaxWidth(Double.MAX_VALUE);
        HBox first = new HBox(10, search, export);
        HBox second = new HBox(10, account, category, from, to, clear);
        first.setAlignment(Pos.CENTER_LEFT);
        second.setAlignment(Pos.CENTER_LEFT);
        VBox filters = new VBox(10, first, second);

        buildTable();
        VBox card = Ui.card(filters, summary, table);
        VBox.setVgrow(table, javafx.scene.layout.Priority.ALWAYS);
        table.setPrefHeight(560);

        reload();
        return Ui.page(new VBox(20, new VBox(4, Ui.pageTitle("Transaction history"),
                Ui.muted("Every deposit, transfer and charge on your accounts.")), card));
    }

    private void buildTable() {
        table.getColumns().setAll(List.of(
                Tables.<LedgerEntry>column("Date", 170, e -> Ui.label(Formats.dateTime(e.createdAt(), zone))),
                Tables.<LedgerEntry>column("Description", 320, HistoryPage::description),
                Tables.<LedgerEntry>column("Account", 100, e -> Ui.label(e.accountType().label())),
                Tables.<LedgerEntry>column("Reference", 140, e -> Ui.label(e.reference(), Styles.TEXT_MUTED, "mono")),
                Tables.<LedgerEntry>column("Amount", 150, e -> Ui.amount(e.amount()), Pos.CENTER_RIGHT),
                Tables.<LedgerEntry>column("Balance", 150, e -> Ui.label(e.balanceAfter().format()), Pos.CENTER_RIGHT)));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getStyleClass().addAll(Styles.STRIPED, Tweaks.EDGE_TO_EDGE);
        table.setPlaceholder(Ui.empty(Material2OutlinedMZ.SEARCH, "No matching transactions",
                "Try a different search or clear the filters."));
    }

    private void reload() {
        AccountType acct = account.getValue() instanceof AccountType t ? t : null;
        TxKind.Category cat = category.getValue() instanceof TxKind.Category c ? c : null;
        var filter = new LedgerService.HistoryFilter(acct, cat, search.getText(), from.getValue(), to.getValue());
        int request = ++generation;
        Async.run(() -> ctx.ledger().search(user.id(), filter), entries -> {
            if (request != generation) {
                return; // a newer search is on its way
            }
            table.setItems(FXCollections.observableArrayList(entries));
            Money in = entries.stream().map(LedgerEntry::amount).filter(Money::isPositive).reduce(Money.ZERO, Money::plus);
            Money out = entries.stream().map(LedgerEntry::amount).filter(Money::isNegative).reduce(Money.ZERO, Money::plus);
            summary.setText(entries.size() + " transaction" + (entries.size() == 1 ? "" : "s")
                    + (entries.size() >= LedgerService.MAX_RESULTS ? " (showing the latest " + LedgerService.MAX_RESULTS + ")" : "")
                    + "   ·   In " + in.format() + "   ·   Out " + out.abs().format());
        }, message -> summary.setText(message));
    }

    private static Node description(LedgerEntry e) {
        Label title = Ui.label(e.description(), Styles.TEXT_BOLD);
        String detail = e.note() != null ? e.note() : (e.counterparty() != null ? e.counterparty() : "");
        VBox box = new VBox(1, title);
        if (!detail.isEmpty()) {
            box.getChildren().add(Ui.label(detail, Styles.TEXT_MUTED, Styles.TEXT_SMALL));
        }
        StackPane badge = new StackPane(Ui.icon(LedgerRow.iconFor(e.kind()), 15));
        badge.getStyleClass().addAll("tx-badge", "small", LedgerRow.styleFor(e.kind()));
        badge.setMinSize(28, 28);
        badge.setMaxSize(28, 28);
        HBox row = new HBox(10, badge, box);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }
}
