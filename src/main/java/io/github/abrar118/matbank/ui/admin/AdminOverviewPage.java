package io.github.abrar118.matbank.ui.admin;

import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.domain.AuditEvent;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.AdminService;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Page;
import io.github.abrar118.matbank.ui.Ui;
import io.github.abrar118.matbank.ui.Workspace;
import io.github.abrar118.matbank.ui.components.Charts;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.time.LocalDate;
import java.util.List;

/** Admin landing page: bank-wide numbers, money movement and recent admin actions. */
public final class AdminOverviewPage implements Page {

    private final Workspace workspace;
    private final AppContext ctx;
    private final User admin;

    public AdminOverviewPage(Workspace workspace) {
        this.workspace = workspace;
        this.ctx = workspace.context();
        this.admin = workspace.user();
    }

    @Override
    public Node view() {
        AdminService.Overview o = ctx.admin().overview(admin, 14);
        String rating = ctx.support().averageRating().stream().mapToObj(r -> String.format("%.1f / 5", r))
                .findFirst().orElse("No ratings");

        Button newClient = Ui.primary("New client", Material2OutlinedMZ.PERSON_ADD);
        newClient.setOnAction(e -> {
            workspace.navigate(Workspace.CLIENTS);
            ClientsPage.openCreateDialog(workspace);
        });
        HBox header = new HBox(new VBox(4, Ui.pageTitle("Good to see you, " + admin.firstName()),
                Ui.muted("A snapshot of MAT Bank today.")), Ui.spacer(), newClient);
        header.setAlignment(Pos.CENTER_LEFT);

        HBox stats = new HBox(14,
                Ui.stat(Material2OutlinedMZ.PEOPLE, "Clients", String.valueOf(o.clients()), null),
                Ui.stat(Material2OutlinedAL.ACCOUNT_BALANCE, "Deposits held", o.holdings().format(), null),
                Ui.stat(Material2OutlinedAL.LOGIN, "Sign-ins today", String.valueOf(o.signInsToday()),
                        o.failedSignInsToday() + " failed"),
                Ui.stat(Material2OutlinedAL.INBOX, "Unread messages", String.valueOf(o.unreadMessages()),
                        "Average rating " + rating));

        var chart = Charts.counts(o.volume(), v -> Formats.SHORT_DATE.format(v.date()),
                List.of("Deposits", "Transfers"),
                List.of(AdminService.DailyVolume::depositCount, AdminService.DailyVolume::transferCount));
        Money moved = o.volume().stream().map(v -> v.deposits().plus(v.transfers())).reduce(Money.ZERO, Money::plus);
        VBox chartCard = Ui.card("Transactions per day, last 14 days", null, chart,
                Ui.muted(moved.format() + " moved in deposits and transfers over the period."));
        HBox.setHgrow(chartCard, Priority.ALWAYS);

        VBox audit = new VBox(10);
        for (AuditEvent e : ctx.admin().auditLog(admin, 6)) {
            VBox text = new VBox(2, Ui.label(AuditLogPage.describe(e.action()), Styles.TEXT_BOLD),
                    Ui.wrapped(e.details() == null ? "" : e.details(), Styles.TEXT_MUTED, Styles.TEXT_SMALL),
                    Ui.label(Formats.friendly(e.createdAt(), ctx.clock().getZone(), LocalDate.now(ctx.clock()))
                            + (e.actorEmail() == null ? "" : "  ·  " + e.actorEmail()), Styles.TEXT_MUTED,
                            Styles.TEXT_SMALL));
            audit.getChildren().add(text);
        }
        Button all = Ui.flat("Audit log", null);
        all.setOnAction(e -> workspace.navigate(Workspace.AUDIT));
        VBox auditCard = Ui.card("Recent activity", all, audit);
        auditCard.setPrefWidth(380);
        auditCard.setMinWidth(340);

        return Ui.page(new VBox(20, header, stats, new HBox(20, chartCard, auditCard)));
    }
}
