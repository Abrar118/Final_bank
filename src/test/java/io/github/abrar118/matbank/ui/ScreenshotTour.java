package io.github.abrar118.matbank.ui;

import io.github.abrar118.matbank.demo.DemoSeeder;
import io.github.abrar118.matbank.domain.Role;
import javafx.application.Platform;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Regenerates the README screenshots from the demo data:
 * {@code xvfb-run -s "-screen 0 1600x1000x24" mvn -q test-compile exec:java ...} or run {@code main} from the IDE.
 *
 * @see UiHarness
 */
public final class ScreenshotTour {

    private ScreenshotTour() {
    }

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "docs/screenshots");
        Path dir = Files.createTempDirectory("matbank-tour");
        UiHarness ui = UiHarness.start(dir, 1366, 860);
        try {
            ui.run(() -> Theme.apply(Theme.LIGHT, ui.window.scene()));
            ui.run(ui.window::showWelcome);
            Thread.sleep(1200);
            ui.screenshot(out.resolve("welcome.png"));
            ui.run(ui.window::showLogin);
            Thread.sleep(600);
            ui.screenshot(out.resolve("sign-in.png"));

            ui.signIn("rahim@matbank.demo", DemoSeeder.CLIENT_PASSWORD, Role.CLIENT, null);
            shoot(ui, out, Workspace.DASHBOARD, "dashboard.png");
            shoot(ui, out, Workspace.TRANSFER, "send-money.png");
            shoot(ui, out, Workspace.DEPOSIT, "deposit.png");
            shoot(ui, out, Workspace.HISTORY, "history.png");
            shoot(ui, out, Workspace.SCHEDULED, "scheduled-payments.png");
            shoot(ui, out, Workspace.STATEMENTS, "statements.png");
            shoot(ui, out, Workspace.CURRENCY, "currency.png");
            shoot(ui, out, Workspace.HELP, "help-center.png");
            shoot(ui, out, Workspace.PROFILE, "profile.png");
            shoot(ui, out, Workspace.ABOUT, "about.png");

            ui.run(() -> Theme.apply(Theme.DARK, ui.window.scene()));
            shoot(ui, out, Workspace.DASHBOARD, "dashboard-dark.png");

            ui.signIn(DemoSeeder.ADMIN_EMAIL, DemoSeeder.ADMIN_PASSWORD, Role.ADMIN, DemoSeeder.ADMIN_PIN);
            shoot(ui, out, Workspace.OVERVIEW, "admin-overview-dark.png");
            ui.run(() -> Theme.apply(Theme.LIGHT, ui.window.scene()));
            shoot(ui, out, Workspace.OVERVIEW, "admin-overview.png");
            shoot(ui, out, Workspace.CLIENTS, "admin-clients.png");
            shoot(ui, out, Workspace.INBOX, "admin-inbox.png");
            shoot(ui, out, Workspace.LOGINS, "admin-sign-ins.png");
            shoot(ui, out, Workspace.AUDIT, "admin-audit-log.png");
        } finally {
            ui.close();
            Platform.exit();
        }
    }

    private static void shoot(UiHarness ui, Path out, String page, String file) throws Exception {
        ui.run(() -> ui.window.workspace().navigate(page));
        Thread.sleep(500);
        ui.settle();
        ui.screenshot(out.resolve(file));
        System.out.println("Saved " + out.resolve(file));
    }
}
