package io.github.abrar118.matbank.ui.common;

import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.domain.Account;
import io.github.abrar118.matbank.domain.Gender;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.ProfileService;
import io.github.abrar118.matbank.service.Validation;
import io.github.abrar118.matbank.ui.Async;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Page;
import io.github.abrar118.matbank.ui.Ui;
import io.github.abrar118.matbank.ui.Workspace;
import io.github.abrar118.matbank.ui.components.Inputs;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Profile details, picture, accounts and security settings (password, and PIN for admins). */
public final class ProfilePage implements Page {

    private final Workspace workspace;
    private final AppContext ctx;
    private final User user;

    public ProfilePage(Workspace workspace) {
        this.workspace = workspace;
        this.ctx = workspace.context();
        this.user = workspace.user();
    }

    @Override
    public Node view() {
        TabPane tabs = new TabPane();
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabs.getTabs().add(new Tab("About", about()));
        if (!user.isAdmin()) {
            tabs.getTabs().add(new Tab("Accounts", accounts()));
        }
        tabs.getTabs().add(new Tab("Edit profile", edit()));
        tabs.getTabs().add(new Tab("Security", security()));
        return Ui.page(new VBox(20, header(), tabs));
    }

    private Node header() {
        Button change = Ui.secondary("Change photo", Material2OutlinedMZ.PHOTO_CAMERA);
        change.setOnAction(e -> choosePhoto());
        Button remove = Ui.flat("Remove", Material2OutlinedAL.DELETE);
        remove.setOnAction(e -> {
            ctx.profiles().setAvatar(user.id(), null);
            photoChanged("Photo removed.");
        });
        remove.setDisable(ctx.profiles().avatar(user.id()).isEmpty());

        VBox text = new VBox(4, Ui.label(user.fullName(), Styles.TITLE_2),
                Ui.muted(user.email()),
                new HBox(8, Ui.chip(user.isAdmin() ? "Administrator" : "Client", Styles.ACCENT),
                        Ui.label("Member since " + Formats.date(user.createdAt(), ctx.clock().getZone()),
                                Styles.TEXT_MUTED)));
        HBox row = new HBox(20, workspace.window().avatars().of(user, 96), text, Ui.spacer(), remove, change);
        row.setAlignment(Pos.CENTER_LEFT);
        return Ui.card(row);
    }

    private Node about() {
        GridPane grid = details(new String[][]{
                {"Full name", user.fullName()},
                {"Email", user.email()},
                {"Gender", user.gender().label()},
                {"Date of birth", Formats.date(user.birthDate())},
                {"Phone", orDash(user.phone())},
                {"Facebook", orDash(user.facebook())},
                {"Address", orDash(user.address())}});
        return padded(Ui.card(grid));
    }

    private Node accounts() {
        VBox list = new VBox(12);
        for (Account a : ctx.banking().accounts(user.id())) {
            HBox row = new HBox(14, Ui.icon(Material2OutlinedAL.ACCOUNT_BALANCE, 22),
                    new VBox(2, Ui.label(a.type().label() + " account", Styles.TEXT_BOLD),
                            Ui.label("No. " + a.number(), Styles.TEXT_MUTED, "mono")),
                    Ui.spacer(), Ui.label(a.balance().format(), Styles.TITLE_4));
            row.setAlignment(Pos.CENTER_LEFT);
            list.getChildren().add(row);
        }
        return padded(Ui.card(list));
    }

    private Node edit() {
        TextField name = new TextField(user.fullName());
        ComboBox<Gender> gender = new ComboBox<>();
        gender.getItems().setAll(Gender.values());
        gender.setValue(user.gender());
        DatePicker birth = Inputs.date(user.birthDate());
        TextField phone = new TextField(orEmpty(user.phone()));
        TextField facebook = new TextField(orEmpty(user.facebook()));
        facebook.setPromptText("facebook.com/yourname");
        TextField address = new TextField(orEmpty(user.address()));

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(14);
        form.add(Ui.field("Full name", name), 0, 0);
        form.add(Ui.field("Gender", gender), 1, 0);
        form.add(Ui.field("Date of birth", birth), 0, 1);
        form.add(Ui.field("Phone", phone), 1, 1);
        form.add(Ui.field("Facebook", facebook), 0, 2);
        form.add(Ui.field("Address", address), 1, 2);
        form.getColumnConstraints().addAll(half(), half());

        Button save = Ui.primary("Save changes", Material2OutlinedAL.CHECK_CIRCLE);
        save.setOnAction(e -> Async.run(save, () -> ctx.profiles().update(user.id(), new ProfileService.ProfileUpdate(
                name.getText(), gender.getValue(), birth.getValue(), phone.getText(), facebook.getText(),
                address.getText())), updated -> {
            workspace.reloadUser();
            workspace.window().notifier().success("Profile updated.");
            workspace.navigate(Workspace.PROFILE);
        }, workspace.window().notifier()::error));
        return padded(Ui.card(form, save));
    }

    private Node security() {
        PasswordTextField current = new PasswordTextField();
        PasswordTextField next = new PasswordTextField();
        PasswordTextField confirm = new PasswordTextField();
        Button change = Ui.primary("Change password", Material2OutlinedAL.LOCK);
        change.setOnAction(e -> {
            if (!next.getPassword().equals(confirm.getPassword())) {
                workspace.window().notifier().error("The new passwords don't match.");
                return;
            }
            String cur = current.getPassword();
            String now = next.getPassword();
            Async.run(change, () -> {
                ctx.auth().changePassword(user.id(), cur, now);
                return true;
            }, ok -> {
                current.setText("");
                next.setText("");
                confirm.setText("");
                workspace.window().notifier().success("Password changed.");
            }, workspace.window().notifier()::error);
        });
        VBox password = Ui.card("Password", null,
                Ui.field("Current password", current),
                Ui.field("New password", next, "At least " + Validation.MIN_PASSWORD_LENGTH
                        + " characters with letters and numbers."),
                Ui.field("Confirm new password", confirm), change);
        password.setMaxWidth(460);

        HBox row = new HBox(20, password);
        if (user.isAdmin()) {
            PasswordTextField pinPassword = new PasswordTextField();
            PasswordTextField pin = new PasswordTextField();
            Button changePin = Ui.primary("Change PIN", Material2OutlinedMZ.PIN);
            changePin.setOnAction(e -> {
                String pw = pinPassword.getPassword();
                String newPin = pin.getPassword();
                Async.run(changePin, () -> {
                    ctx.auth().changePin(user.id(), pw, newPin);
                    return true;
                }, ok -> {
                    pinPassword.setText("");
                    pin.setText("");
                    workspace.window().notifier().success("Sign-in PIN changed.");
                }, workspace.window().notifier()::error);
            });
            VBox pinCard = Ui.card("Admin PIN", null, Ui.field("Password", pinPassword),
                    Ui.field("New PIN", pin, "4 to 6 digits, asked for at every admin sign-in."), changePin);
            pinCard.setMaxWidth(460);
            HBox.setHgrow(pinCard, Priority.ALWAYS);
            row.getChildren().add(pinCard);
        }
        HBox.setHgrow(password, Priority.ALWAYS);
        return padded(row);
    }

    private void choosePhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose a profile photo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg"));
        File home = new File(System.getProperty("user.home"));
        File pictures = new File(home, "Pictures");
        chooser.setInitialDirectory(pictures.isDirectory() ? pictures : home);
        File file = chooser.showOpenDialog(workspace.window().stage());
        if (file == null) {
            return;
        }
        try {
            byte[] bytes = Files.readAllBytes(file.toPath());
            if (new Image(new ByteArrayInputStream(bytes)).isError()) {
                workspace.window().notifier().error("That file isn't an image MAT Bank can read.");
                return;
            }
            ctx.profiles().setAvatar(user.id(), bytes);
            photoChanged("Photo updated.");
        } catch (IOException | RuntimeException ex) {
            workspace.window().notifier().error(ex instanceof IOException ? "Couldn't read that file."
                    : Async.describe(ex));
        }
    }

    private void photoChanged(String message) {
        workspace.reloadUser();
        workspace.window().notifier().success(message);
        workspace.navigate(Workspace.PROFILE);
    }

    static GridPane details(String[][] rows) {
        GridPane grid = new GridPane();
        grid.setHgap(32);
        grid.setVgap(12);
        for (int i = 0; i < rows.length; i++) {
            grid.add(Ui.label(rows[i][0], Styles.TEXT_MUTED), 0, i);
            Label value = Ui.label(rows[i][1]);
            value.setWrapText(true);
            grid.add(value, 1, i);
        }
        return grid;
    }

    private static Node padded(Node node) {
        VBox box = new VBox(node);
        box.setPadding(new javafx.geometry.Insets(16, 0, 0, 0));
        return box;
    }

    private static javafx.scene.layout.ColumnConstraints half() {
        var c = new javafx.scene.layout.ColumnConstraints();
        c.setPercentWidth(50);
        return c;
    }

    private static String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }
}
