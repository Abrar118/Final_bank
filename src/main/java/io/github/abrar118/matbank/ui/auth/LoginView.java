package io.github.abrar118.matbank.ui.auth;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import io.github.abrar118.matbank.demo.DemoSeeder;
import io.github.abrar118.matbank.domain.Role;
import io.github.abrar118.matbank.service.AuthService;
import io.github.abrar118.matbank.service.AuthService.LoginResult;
import io.github.abrar118.matbank.ui.AppWindow;
import io.github.abrar118.matbank.ui.Async;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Ui;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.time.ZoneId;

/**
 * Sign-in for clients and admins (admins also enter their PIN, as in 2022). The email-OTP "forgot password" flow is
 * gone; admins reset passwords instead, and demo accounts let first-time visitors in with one click.
 */
public final class LoginView {

    private final AppWindow window;
    private final ToggleGroup roleGroup = new ToggleGroup();
    private final ToggleButton clientTab = new ToggleButton("Client");
    private final ToggleButton adminTab = new ToggleButton("Admin");
    private final CustomTextField email = new CustomTextField();
    private final PasswordTextField password = new PasswordTextField();
    private final PasswordTextField pin = new PasswordTextField();
    private final Label error = Ui.wrapped("", Styles.DANGER);
    private final Button signIn = Ui.primary("Sign in", null);
    private VBox pinField;

    public LoginView(AppWindow window) {
        this.window = window;
    }

    public Node build() {
        HBox root = new HBox(sidePanel(), formPanel());
        root.getStyleClass().add("login");
        return root;
    }

    private Node sidePanel() {
        Region image = new Region();
        image.setStyle("-fx-background-image: url('" + Ui.image("skyline.jpg").getUrl() + "');"
                + "-fx-background-size: cover; -fx-background-position: center;");
        Region shade = new Region();
        shade.getStyleClass().add("login-shade");

        Label eyebrow = Ui.label("WELCOME TO", "eyebrow", "on-dark");
        HBox wordmark = Ui.wordmark(48);
        wordmark.getStyleClass().add("on-dark");
        Label tagline = Ui.wrapped("Introducing an easy and safe way to manage your finances.", "on-dark",
                "login-tagline");
        Button back = Ui.flat("Back to home", Material2OutlinedAL.ARROW_BACK);
        back.getStyleClass().add("on-dark");
        back.setOnAction(e -> window.showWelcome());

        VBox text = new VBox(14, back, Ui.spacer(), eyebrow, wordmark, tagline);
        text.setPadding(new Insets(28, 40, 48, 40));
        StackPane side = new StackPane(image, shade, text);
        side.setPrefWidth(500);
        side.setMinWidth(380);
        HBox.setHgrow(side, Priority.SOMETIMES);
        return side;
    }

    private Node formPanel() {
        Label heading = Ui.label("Sign in", Styles.TITLE_1);
        Label sub = Ui.muted("Use your MAT Bank email and password.");

        clientTab.setToggleGroup(roleGroup);
        adminTab.setToggleGroup(roleGroup);
        clientTab.getStyleClass().add(Styles.LEFT_PILL);
        adminTab.getStyleClass().add(Styles.RIGHT_PILL);
        clientTab.setSelected(true);
        clientTab.setPrefWidth(180);
        adminTab.setPrefWidth(180);
        clientTab.setGraphic(Ui.icon(Material2OutlinedMZ.PERSON, 16));
        adminTab.setGraphic(Ui.icon(Material2OutlinedAL.ADMIN_PANEL_SETTINGS, 16));
        roleGroup.selectedToggleProperty().addListener((obs, old, now) -> {
            if (now == null) {
                old.setSelected(true);
                return;
            }
            updateRole();
        });
        HBox roles = new HBox(clientTab, adminTab);

        email.setPromptText("you@example.com");
        email.setLeft(Ui.icon(Material2OutlinedMZ.MAIL, 16));
        password.setPromptText("Password");
        password.setLeft(Ui.icon(Material2OutlinedAL.LOCK, 16));
        password.setRight(revealToggle(password));
        pin.setPromptText("4-6 digit PIN");
        pin.setLeft(Ui.icon(Material2OutlinedMZ.PIN, 16));
        pin.setRight(revealToggle(pin));

        pinField = Ui.field("Admin PIN", pin);
        error.setVisible(false);
        error.setManaged(false);

        signIn.setMaxWidth(Double.MAX_VALUE);
        signIn.getStyleClass().add(Styles.LARGE);
        signIn.setDefaultButton(true);
        signIn.textProperty().bind(Bindings.when(window.readyProperty())
                .then("Sign in").otherwise("Preparing demo bank..."));
        signIn.setOnAction(e -> submit());

        Button forgot = Ui.flat("Forgot password?", null);
        forgot.setOnAction(e -> window.dialogs().message("Forgot your password?",
                "For your security MAT Bank no longer emails one-time codes. Ask an admin to issue a temporary "
                        + "password (Clients > Reset password), then change it from your profile."));
        Button contact = Ui.flat("Contact support", Material2OutlinedMZ.SUPPORT_AGENT);
        contact.setOnAction(e -> window.showVisitorHelp(window::showLogin));
        HBox links = new HBox(8, forgot, Ui.spacer(), contact);
        links.setAlignment(Pos.CENTER_LEFT);

        VBox form = new VBox(16, heading, sub, roles, Ui.field("Email", email), Ui.field("Password", password),
                pinField, error, signIn, links, demoSection());
        form.setMaxWidth(400);
        form.setFillWidth(true);

        updateRole();
        VBox panel = new VBox(form);
        panel.setAlignment(Pos.CENTER);
        panel.setPadding(new Insets(32, 48, 32, 48));
        HBox.setHgrow(panel, Priority.ALWAYS);
        return panel;
    }

    private Node demoSection() {
        Separator line = new Separator();
        HBox.setHgrow(line, Priority.ALWAYS);
        Label caption = Ui.muted("New here? Try a demo account");
        caption.setMinWidth(Region.USE_PREF_SIZE);
        Separator line2 = new Separator();
        HBox.setHgrow(line2, Priority.ALWAYS);
        HBox divider = new HBox(10, line, caption, line2);
        divider.setAlignment(Pos.CENTER);

        FlowPane buttons = new FlowPane(8, 8);
        for (DemoSeeder.DemoLogin demo : DemoSeeder.LOGINS) {
            Button button = Ui.secondary(demo.label(), demo.role() == Role.ADMIN
                    ? Material2OutlinedAL.ADMIN_PANEL_SETTINGS : Material2OutlinedMZ.PERSON);
            button.getStyleClass().add(Styles.SMALL);
            button.disableProperty().bind(window.readyProperty().not());
            button.setOnAction(e -> {
                (demo.role() == Role.ADMIN ? adminTab : clientTab).setSelected(true);
                email.setText(demo.email());
                password.setText(demo.password());
                pin.setText(demo.pin() == null ? "" : demo.pin());
                submit();
            });
            buttons.getChildren().add(button);
        }
        Label hint = Ui.wrapped("Demo clients use the password " + DemoSeeder.CLIENT_PASSWORD + ". The demo admin "
                + "uses " + DemoSeeder.ADMIN_PASSWORD + " with PIN " + DemoSeeder.ADMIN_PIN + ".",
                Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        return new VBox(12, divider, buttons, hint);
    }

    private Node revealToggle(PasswordTextField field) {
        FontIcon eye = Ui.icon(Material2OutlinedMZ.VISIBILITY_OFF, 16);
        eye.setCursor(Cursor.HAND);
        eye.setOnMouseClicked(e -> {
            field.setRevealPassword(!field.getRevealPassword());
            eye.setIconCode(field.getRevealPassword() ? Material2OutlinedMZ.VISIBILITY
                    : Material2OutlinedMZ.VISIBILITY_OFF);
        });
        return eye;
    }

    private void updateRole() {
        boolean admin = adminTab.isSelected();
        pinField.setVisible(admin);
        pinField.setManaged(admin);
        showError(null);
    }

    private void submit() {
        if (!window.readyProperty().get()) {
            return;
        }
        Role role = adminTab.isSelected() ? Role.ADMIN : Role.CLIENT;
        String mail = email.getText();
        if (mail == null || mail.isBlank() || password.getPassword().isEmpty()) {
            showError("Enter your email and password.");
            return;
        }
        if (role == Role.ADMIN && pin.getPassword().isEmpty()) {
            showError("Admins also need their PIN.");
            return;
        }
        showError(null);
        String pass = password.getPassword();
        String pinValue = pin.getPassword();
        Async.run(signIn, () -> window.context().auth().login(mail, pass, role, pinValue), result -> {
            switch (result) {
                case LoginResult.Success s -> window.signIn(s.user());
                case LoginResult.Failure f -> {
                    String extra = f.attemptsLeft() >= 0 && f.attemptsLeft() <= 2
                            ? " " + f.attemptsLeft() + " attempt" + (f.attemptsLeft() == 1 ? "" : "s")
                            + " left before the account is locked for "
                            + AuthService.LOCK_DURATION.toMinutes() + " minutes."
                            : "";
                    showError(f.message() + "." + extra);
                    password.setText("");
                }
                case LoginResult.Locked l -> showError("Too many failed attempts. This account is locked until "
                        + Formats.TIME.format(l.until().atZone(ZoneId.systemDefault())) + ".");
            }
        }, this::showError);
    }

    private void showError(String message) {
        boolean show = message != null && !message.isBlank();
        error.setText(show ? message : "");
        error.setVisible(show);
        error.setManaged(show);
        if (show) {
            Animations.shakeX(error).playFromStart();
        }
    }
}
