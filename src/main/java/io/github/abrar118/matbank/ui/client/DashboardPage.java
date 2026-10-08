package io.github.abrar118.matbank.ui.client;

import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.domain.Account;
import io.github.abrar118.matbank.domain.LedgerEntry;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.RecurringPayment;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.LedgerService;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Page;
import io.github.abrar118.matbank.ui.Ui;
import io.github.abrar118.matbank.ui.Workspace;
import io.github.abrar118.matbank.ui.components.AccountCard;
import io.github.abrar118.matbank.ui.components.Charts;
import io.github.abrar118.matbank.ui.components.LedgerRow;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

/** Client home: account cards, this month's numbers, charts, recent activity and the 2022 quick links. */
public final class DashboardPage implements Page {

    private record QuickLink(String name, String description, String logo, String url) {
    }

    private static final List<QuickLink> LINKS = List.of(
            new QuickLink("Bangladesh Bank", "Exchange rates of the taka", "links/bangladesh-bank.png",
                    "https://www.bb.org.bd/en/index.php/econdata/exchangerate"),
            new QuickLink("MarketWatch", "Stock market prices", "links/marketwatch.png", "https://www.marketwatch.com/"),
            new QuickLink("World Bank", "Finances around the world", "links/world-bank.png",
                    "https://www.worldbank.org/en/home"),
            new QuickLink("The Independent", "24/7 news from Bangladesh", "links/independent.png",
                    "https://www.theindependentbd.com/"));

    private final Workspace workspace;
    private final AppContext ctx;
    private final User user;

    public DashboardPage(Workspace workspace) {
        this.workspace = workspace;
        this.ctx = workspace.context();
        this.user = workspace.user();
    }

    @Override
    public Node view() {
        List<Account> accounts = ctx.banking().accounts(user.id());
        Money total = accounts.stream().map(Account::balance).reduce(Money.ZERO, Money::plus);
        var months = ctx.ledger().monthlyTotals(user.id(), 6);
        var thisMonth = months.getLast();

        VBox content = new VBox(22, header(), cardsRow(accounts, total, thisMonth), chartsRow(months),
                bottomRow());
        return Ui.page(content);
    }

    private Node header() {
        int hour = LocalTime.now(ctx.clock()).getHour();
        String greeting = hour < 12 ? "Good morning" : hour < 17 ? "Good afternoon" : "Good evening";
        VBox text = new VBox(4, Ui.pageTitle(greeting + ", " + user.firstName()),
                Ui.muted("Here's what's happening with your money today."));
        Button send = Ui.primary("Send money", Material2OutlinedMZ.SEND);
        send.setOnAction(e -> workspace.navigate(Workspace.TRANSFER));
        Button deposit = Ui.secondary("Deposit", Material2OutlinedAL.ACCOUNT_BALANCE_WALLET);
        deposit.setOnAction(e -> workspace.navigate(Workspace.DEPOSIT));
        Button statement = Ui.secondary("Statement", Material2OutlinedMZ.PICTURE_AS_PDF);
        statement.setOnAction(e -> workspace.navigate(Workspace.STATEMENTS));
        HBox row = new HBox(10, text, Ui.spacer(), statement, deposit, send);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Node cardsRow(List<Account> accounts, Money total, LedgerService.MonthTotals month) {
        HBox cards = new HBox(16);
        for (Account account : accounts) {
            cards.getChildren().add(AccountCard.of(account, user.fullName()));
        }
        String monthName = YearMonth.now(ctx.clock()).getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        VBox stats = new VBox(12,
                Ui.stat(Material2OutlinedAL.ACCOUNT_BALANCE, "Total balance", total.format(),
                        accounts.size() + " accounts"),
                Ui.stat(Material2OutlinedMZ.TRENDING_UP, "Money in, " + monthName, month.in().format(), null),
                Ui.stat(Material2OutlinedMZ.TRENDING_DOWN, "Money out, " + monthName, month.out().format(), null));
        stats.setMinWidth(240);
        HBox.setHgrow(stats, Priority.ALWAYS);
        HBox row = new HBox(16, cards, stats);
        row.setAlignment(Pos.TOP_LEFT);
        return row;
    }

    private Node chartsRow(List<LedgerService.MonthTotals> months) {
        List<Charts.Point> trend = ctx.ledger().balanceTrend(user.id(), 60).stream()
                .map(d -> new Charts.Point(d.date(), d.total())).toList();
        VBox balanceCard = Ui.card("Total balance, last 60 days", null, Charts.trend("Balance", trend));
        HBox.setHgrow(balanceCard, Priority.ALWAYS);
        balanceCard.setPrefWidth(560);

        var bars = Charts.bars(months, m -> Formats.MONTH.format(m.month()), List.of("Money in", "Money out"),
                List.of(LedgerService.MonthTotals::in, LedgerService.MonthTotals::out));
        VBox monthsCard = Ui.card("In and out by month", null, bars);
        HBox.setHgrow(monthsCard, Priority.ALWAYS);
        monthsCard.setPrefWidth(420);
        return new HBox(16, balanceCard, monthsCard);
    }

    private Node bottomRow() {
        LocalDate today = LocalDate.now(ctx.clock());
        List<LedgerEntry> recent = ctx.ledger().recent(user.id(), 7);
        VBox list = new VBox(2);
        if (recent.isEmpty()) {
            list.getChildren().add(Ui.empty(Material2OutlinedAL.HISTORY, "No transactions yet",
                    "Deposits and transfers will show up here."));
        }
        for (LedgerEntry e : recent) {
            list.getChildren().add(LedgerRow.of(e, ctx.clock().getZone(), today));
        }
        Button all = Ui.flat("See all", Material2OutlinedAL.ARROW_FORWARD);
        all.setContentDisplay(ContentDisplay.RIGHT);
        all.setOnAction(e -> workspace.navigate(Workspace.HISTORY));
        VBox recentCard = Ui.card("Recent transactions", all, list);
        HBox.setHgrow(recentCard, Priority.ALWAYS);

        VBox side = new VBox(16, upcoming(), quickLinks());
        side.setPrefWidth(340);
        side.setMinWidth(320);
        return new HBox(16, recentCard, side);
    }

    private Node upcoming() {
        List<RecurringPayment> payments = ctx.recurring().list(user.id()).stream()
                .filter(RecurringPayment::active).limit(3).toList();
        VBox list = new VBox(10);
        if (payments.isEmpty()) {
            list.getChildren().add(Ui.muted("No scheduled payments."));
        }
        for (RecurringPayment p : payments) {
            VBox text = new VBox(2, Ui.label(p.note() == null ? p.recipientEmail() : p.note(), Styles.TEXT_BOLD),
                    Ui.label("Next on " + Formats.date(p.nextRun()) + "  ·  " + p.frequency().label(),
                            Styles.TEXT_MUTED, Styles.TEXT_SMALL));
            HBox row = new HBox(10, Ui.icon(Material2OutlinedMZ.REPEAT, 18), text, Ui.spacer(),
                    Ui.label(p.amount().format(), Styles.TEXT_BOLD));
            row.setAlignment(Pos.CENTER_LEFT);
            list.getChildren().add(row);
        }
        Button manage = Ui.flat("Manage", null);
        manage.setOnAction(e -> workspace.navigate(Workspace.SCHEDULED));
        return Ui.card("Upcoming payments", manage, list);
    }

    private Node quickLinks() {
        VBox list = new VBox(10);
        for (QuickLink link : LINKS) {
            ImageView logo = Ui.imageView(link.logo(), 32);
            VBox text = new VBox(2, Ui.label(link.name(), Styles.TEXT_BOLD),
                    Ui.label(link.description(), Styles.TEXT_MUTED, Styles.TEXT_SMALL));
            Button open = Ui.iconButton(Material2OutlinedMZ.OPEN_IN_NEW, "Open " + link.name());
            open.setOnAction(e -> Ui.open(link.url()));
            HBox row = new HBox(12, logo, text, Ui.spacer(), open);
            row.setAlignment(Pos.CENTER_LEFT);
            list.getChildren().add(row);
        }
        return Ui.card("Quick links", null, list);
    }
}
