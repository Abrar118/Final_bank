package io.github.abrar118.matbank.ui.auth;

import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.ui.AppWindow;
import io.github.abrar118.matbank.ui.Ui;
import javafx.scene.layout.VBox;

/** The 2022 "Terms of usage and privacy policy" screen, updated with what the app actually does with data. */
public final class PolicyDialog {

    private PolicyDialog() {
    }

    public static void show(AppWindow window) {
        String dbFile = window.context().database().file().toString();
        VBox body = new VBox(12,
                Ui.wrapped("Please read these terms carefully before you start to use the app. By using the app, you "
                        + "accept and agree to be bound and abide by these terms and our privacy policy."),
                Ui.label("A demo, not a real bank", Styles.TEXT_BOLD),
                Ui.wrapped("MAT Bank started as a university project and is kept as a portfolio piece. No real money "
                        + "moves. Don't enter real financial or personal information.", Styles.TEXT_MUTED),
                Ui.label("Where your data lives", Styles.TEXT_BOLD),
                Ui.wrapped("Everything stays on this computer in one database file:\n" + dbFile, Styles.TEXT_MUTED),
                Ui.label("How passwords are kept", Styles.TEXT_BOLD),
                Ui.wrapped("Passwords and admin PINs are stored only as salted bcrypt hashes. After five wrong "
                        + "attempts an account is locked for five minutes.", Styles.TEXT_MUTED),
                Ui.label("Network use", Styles.TEXT_BOLD),
                Ui.wrapped("The currency converter downloads public exchange rates from open.er-api.com. No "
                        + "personal data is sent.", Styles.TEXT_MUTED));
        window.dialogs().info("Terms of usage and privacy policy", body);
    }
}
