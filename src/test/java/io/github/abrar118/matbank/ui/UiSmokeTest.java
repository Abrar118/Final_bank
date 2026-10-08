package io.github.abrar118.matbank.ui;

import io.github.abrar118.matbank.demo.DemoSeeder;
import io.github.abrar118.matbank.domain.AccountType;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.Role;
import io.github.abrar118.matbank.domain.User;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real JavaFX UI: every screen as a client and as an admin in both themes, plus end-to-end sign-in,
 * transfer and deposit flows checked against the database. Runs only when a display is available (CI uses
 * xvfb-run).
 */
@EnabledIfEnvironmentVariable(named = "DISPLAY", matches = ".+")
class UiSmokeTest {

    private static final List<String> CLIENT_PAGES = List.of(Workspace.DASHBOARD, Workspace.TRANSFER,
            Workspace.DEPOSIT, Workspace.HISTORY, Workspace.SCHEDULED, Workspace.STATEMENTS, Workspace.CURRENCY,
            Workspace.HELP, Workspace.PROFILE, Workspace.ABOUT);
    private static final List<String> ADMIN_PAGES = List.of(Workspace.OVERVIEW, Workspace.CLIENTS,
            Workspace.INBOX, Workspace.LOGINS, Workspace.AUDIT, Workspace.PROFILE, Workspace.ABOUT);

    @TempDir
    Path dir;

    @Test
    void everyScreenRendersInBothThemes() throws Exception {
        UiHarness ui = UiHarness.start(dir, 1280, 820);
        try {
            List<String> failures = new ArrayList<>();
            for (Theme theme : Theme.values()) {
                ui.run(() -> Theme.apply(theme, ui.window.scene()));
                ui.run(ui.window::showWelcome);
                ui.run(ui.window::showLogin);

                ui.signIn("rahim@matbank.demo", DemoSeeder.CLIENT_PASSWORD, Role.CLIENT, null);
                visit(ui, CLIENT_PAGES, theme, failures);

                ui.signIn(DemoSeeder.ADMIN_EMAIL, DemoSeeder.ADMIN_PASSWORD, Role.ADMIN, DemoSeeder.ADMIN_PIN);
                visit(ui, ADMIN_PAGES, theme, failures);
            }
            assertThat(failures).isEmpty();
        } finally {
            ui.close();
        }
    }

    @Test
    void clientSignsInWithADemoButtonThenSendsMoneyAndDeposits() throws Exception {
        UiHarness ui = UiHarness.start(dir, 1280, 820);
        try {
            ui.run(ui.window::showLogin);
            ui.run(() -> button(ui, "Rahim (client)").fire());
            waitFor(ui, () -> ui.window.workspace() != null);
            User rahim = ui.window.workspace().user();
            assertThat(rahim.email()).isEqualTo("rahim@matbank.demo");
            User nusrat = ui.ctx.banking().findRecipient("nusrat@matbank.demo").orElseThrow();
            Money rahimBefore = ui.ctx.banking().account(rahim.id(), AccountType.CHECKING).balance();
            Money nusratBefore = ui.ctx.banking().account(nusrat.id(), AccountType.CHECKING).balance();

            // Send money: fill the form, review, confirm in the dialog.
            ui.run(() -> ui.window.workspace().navigate(Workspace.TRANSFER));
            ui.run(() -> {
                field(ui, "recipient@example.com").setText("nusrat@matbank.demo");
                field(ui, "0.00").setText("100");
                field(ui, "What's it for? (optional)").setText("UI test");
            });
            ui.run(() -> button(ui, "Review transfer").fire());
            ui.run(() -> dialogButton(ui, "Send money").fire());
            waitFor(ui, () -> ui.ctx.banking().account(nusrat.id(), AccountType.CHECKING).balance()
                    .equals(nusratBefore.plus(Money.of(100))));
            assertThat(ui.ctx.banking().account(rahim.id(), AccountType.CHECKING).balance())
                    .isEqualTo(rahimBefore.minus(Money.of(102)));
            ui.run(() -> dialogButton(ui, "OK").fire());

            // Deposit wizard: amount, reference, receipt.
            ui.run(() -> ui.window.workspace().navigate(Workspace.DEPOSIT));
            ui.run(() -> field(ui, "0.00").setText("500"));
            ui.run(() -> button(ui, "Next").fire());
            ui.run(() -> field(ui, "e.g. Salary - Padma Software Ltd").setText("UI test deposit"));
            ui.run(() -> button(ui, "Review").fire());
            ui.run(() -> button(ui, "Confirm deposit").fire());
            waitFor(ui, () -> ui.ctx.banking().account(rahim.id(), AccountType.CHECKING).balance()
                    .equals(rahimBefore.minus(Money.of(102)).plus(Money.of(490))));
        } finally {
            ui.close();
        }
    }

    @Test
    void wrongPasswordShowsAnErrorOnTheSignInScreen() throws Exception {
        UiHarness ui = UiHarness.start(dir, 1280, 820);
        try {
            ui.run(ui.window::showLogin);
            ui.run(() -> {
                field(ui, "you@example.com").setText("rahim@matbank.demo");
                ((TextField) find(ui.window.scene().getRoot(), n -> n instanceof TextField t
                        && "Password".equals(t.getPromptText()))).setText("wrong-password1");
                button(ui, "Sign in").fire();
            });
            waitFor(ui, () -> containsTextStartingWith(ui.window.scene().getRoot(), "Incorrect email or password"));
            assertThat(ui.window.workspace()).isNull();
        } finally {
            ui.close();
        }
    }

    private static Button button(UiHarness ui, String text) {
        return (Button) find(ui.window.scene().getRoot(), n -> n instanceof Button b && text.equals(b.getText())
                && b.isVisible() && !b.isDisabled());
    }

    private static Button dialogButton(UiHarness ui, String text) {
        Node dialog = find(ui.window.scene().getRoot(), n -> n.getStyleClass().contains("dialog"));
        return (Button) find(dialog, n -> n instanceof Button b && text.equals(b.getText()));
    }

    private static TextField field(UiHarness ui, String prompt) {
        return (TextField) find(ui.window.scene().getRoot(), n -> n instanceof TextField t
                && prompt.equals(t.getPromptText()) && t.isVisible());
    }

    private static Node find(Node root, Predicate<Node> match) {
        Node found = findOrNull(root, match);
        if (found == null) {
            throw new AssertionError("No matching node on screen");
        }
        return found;
    }

    private static Node findOrNull(Node node, Predicate<Node> match) {
        if (match.test(node)) {
            return node;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Node found = findOrNull(child, match);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean containsTextStartingWith(Node node, String prefix) {
        return findOrNull(node, n -> n instanceof Label l && l.getText() != null && l.getText().startsWith(prefix))
                != null;
    }

    private static void waitFor(UiHarness ui, Callable<Boolean> condition) throws Exception {
        for (int i = 0; i < 50; i++) {
            if (UiHarness.fx(condition)) {
                return;
            }
            ui.settle();
        }
        throw new AssertionError("Condition not met in time");
    }

    private static void visit(UiHarness ui, List<String> pages, Theme theme, List<String> failures) throws Exception {
        for (String page : pages) {
            ui.run(() -> ui.window.workspace().navigate(page));
            boolean broken = UiHarness.fx(() -> containsText(ui.window.scene().getRoot(),
                    "This page could not be loaded"));
            if (broken) {
                failures.add(theme + "/" + page);
            }
            assertThat(ui.snapshot().getWidth()).isPositive();
        }
    }

    private static boolean containsText(Node node, String text) {
        if (node instanceof Label label && text.equals(label.getText())) {
            return true;
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                if (containsText(child, text)) {
                    return true;
                }
            }
        }
        return false;
    }
}
