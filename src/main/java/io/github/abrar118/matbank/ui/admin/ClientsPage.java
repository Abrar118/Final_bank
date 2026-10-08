package io.github.abrar118.matbank.ui.admin;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.controls.PasswordTextField;
import atlantafx.base.theme.Styles;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.domain.Account;
import io.github.abrar118.matbank.domain.Gender;
import io.github.abrar118.matbank.domain.LedgerEntry;
import io.github.abrar118.matbank.domain.Money;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.AdminService;
import io.github.abrar118.matbank.service.Validation;
import io.github.abrar118.matbank.ui.Async;
import io.github.abrar118.matbank.ui.Formats;
import io.github.abrar118.matbank.ui.Page;
import io.github.abrar118.matbank.ui.Ui;
import io.github.abrar118.matbank.ui.Workspace;
import io.github.abrar118.matbank.ui.components.Inputs;
import io.github.abrar118.matbank.ui.components.LedgerRow;
import javafx.animation.PauseTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.time.LocalDate;

/** Client list with search, details, and the admin actions: open, reset password, close. */
public final class ClientsPage implements Page {

    private final Workspace workspace;
    private final AppContext ctx;
    private final User admin;
    private final String initialSearch;
    private final ListView<User> list = new ListView<>();
    private final Label count = Ui.muted("");
    private final VBox details = new VBox(16);

    public ClientsPage(Workspace workspace, String initialSearch) {
        this.workspace = workspace;
        this.ctx = workspace.context();
        this.admin = workspace.user();
        this.initialSearch = initialSearch;
    }

    @Override
    public Node view() {
        CustomTextField search = new CustomTextField(initialSearch == null ? "" : initialSearch);
        search.setPromptText("Search by name or email");
        search.setLeft(Ui.icon(Material2OutlinedMZ.SEARCH, 16));
        PauseTransition debounce = new PauseTransition(Duration.millis(200));
        debounce.setOnFinished(e -> load(search.getText(), null));
        search.textProperty().addListener((obs, o, n) -> debounce.playFromStart());

        list.setCellFactory(v -> new ListCell<>() {
            @Override
            protected void updateItem(User u, boolean empty) {
                super.updateItem(u, empty);
                if (empty || u == null) {
                    setGraphic(null);
                    return;
                }
                VBox text = new VBox(1, Ui.label(u.fullName(), Styles.TEXT_BOLD),
                        Ui.label(u.email(), Styles.TEXT_MUTED, Styles.TEXT_SMALL));
                HBox row = new HBox(10, workspace.window().avatars().of(u, 34), text);
                row.setAlignment(Pos.CENTER_LEFT);
                row.setPadding(new Insets(4, 2, 4, 2));
                setGraphic(row);
            }
        });
        list.setPlaceholder(Ui.empty(Material2OutlinedMZ.PEOPLE, "No clients found", "Try another search."));
        list.getSelectionModel().selectedItemProperty().addListener((obs, o, n) -> showDetails(n));
        list.setPrefHeight(600);

        Button add = Ui.primary("New client", Material2OutlinedMZ.PERSON_ADD);
        add.setOnAction(e -> openCreateDialog(workspace));
        list.getItems().addListener((javafx.collections.ListChangeListener<User>) change ->
                count.setText(list.getItems().size() + (list.getItems().size() == 1 ? " client" : " clients")));

        VBox left = Ui.card(new HBox(10, Ui.sectionTitle("Clients"), Ui.spacer(), add), search, count, list);
        left.setPrefWidth(360);
        left.setMinWidth(320);
        VBox.setVgrow(list, Priority.ALWAYS);

        VBox right = new VBox(details);
        HBox.setHgrow(right, Priority.ALWAYS);

        load(search.getText(), null);
        return Ui.page(new HBox(20, left, right));
    }

    private void load(String search, Long selectId) {
        var clients = ctx.admin().clients(admin, search);
        list.getItems().setAll(clients);
        if (clients.isEmpty()) {
            showDetails(null);
            return;
        }
        User select = selectId == null ? clients.getFirst()
                : clients.stream().filter(u -> u.id() == selectId).findFirst().orElse(clients.getFirst());
        list.getSelectionModel().select(select);
    }

    private void showDetails(User client) {
        if (client == null) {
            details.getChildren().setAll(Ui.card(Ui.empty(Material2OutlinedMZ.PERSON, "Select a client",
                    "Their profile, accounts and recent transactions appear here.")));
            return;
        }
        AdminService.ClientDetails d = ctx.admin().clientDetails(admin, client.id());
        User c = d.client();

        Button reset = Ui.secondary("Reset password", Material2OutlinedMZ.VPN_KEY);
        reset.setOnAction(e -> resetPassword(c));
        Button close = Ui.secondary("Close account", Material2OutlinedAL.DELETE);
        close.getStyleClass().add(Styles.DANGER);
        close.setOnAction(e -> closeAccount(c, d.totalBalance()));

        VBox who = new VBox(4, Ui.label(c.fullName(), Styles.TITLE_3), Ui.muted(c.email()),
                Ui.label("Client since " + Formats.date(c.createdAt(), ctx.clock().getZone()),
                        Styles.TEXT_MUTED, Styles.TEXT_SMALL));
        HBox header = new HBox(16, workspace.window().avatars().of(c, 72), who, Ui.spacer(), reset, close);
        header.setAlignment(Pos.CENTER_LEFT);

        GridPane info = new GridPane();
        info.setHgap(28);
        info.setVgap(10);
        String[][] rows = {
                {"Gender", c.gender().label()}, {"Date of birth", Formats.date(c.birthDate())},
                {"Phone", dash(c.phone())}, {"Facebook", dash(c.facebook())}, {"Address", dash(c.address())}};
        for (int i = 0; i < rows.length; i++) {
            info.add(Ui.label(rows[i][0], Styles.TEXT_MUTED), (i % 2) * 2, i / 2);
            info.add(Ui.label(rows[i][1]), (i % 2) * 2 + 1, i / 2);
        }

        HBox balances = new HBox(14);
        for (Account a : d.accounts()) {
            balances.getChildren().add(Ui.stat(Material2OutlinedAL.ACCOUNT_BALANCE, a.type().label() + "  ·  "
                    + a.maskedNumber(), a.balance().format(), null));
        }
        balances.getChildren().add(Ui.stat(Material2OutlinedMZ.MONETIZATION_ON, "Total", d.totalBalance().format(), null));

        VBox recent = new VBox(2);
        LocalDate today = LocalDate.now(ctx.clock());
        for (LedgerEntry e : d.recent().stream().limit(12).toList()) {
            recent.getChildren().add(LedgerRow.of(e, ctx.clock().getZone(), today));
        }
        if (d.recent().isEmpty()) {
            recent.getChildren().add(Ui.muted("No transactions yet."));
        }

        details.getChildren().setAll(Ui.card(header, info), balances,
                Ui.card("Recent transactions", null, recent));
    }

    private void resetPassword(User client) {
        workspace.window().dialogs().confirm("Reset password for " + client.firstName() + "?",
                "MAT Bank will generate a temporary password and unlock the account. Share it with "
                        + client.fullName() + " in person or by phone; they should change it after signing in.",
                "Generate password", false, h -> Async.run(() -> ctx.auth().resetClientPassword(admin, client.id()),
                        temporary -> {
                            h.close();
                            showTemporaryPassword(client, temporary);
                        }, h::error));
    }

    private void showTemporaryPassword(User client, String temporary) {
        Label value = Ui.label(temporary, "temp-password");
        Button copy = Ui.secondary("Copy", Material2OutlinedAL.CONTENT_COPY);
        copy.setOnAction(e -> {
            ClipboardContent content = new ClipboardContent();
            content.putString(temporary);
            Clipboard.getSystemClipboard().setContent(content);
            workspace.window().notifier().success("Copied to clipboard.");
        });
        HBox row = new HBox(12, value, copy);
        row.setAlignment(Pos.CENTER_LEFT);
        workspace.window().dialogs().info("Temporary password", new VBox(12,
                Ui.wrapped("Temporary password for " + client.fullName() + " (" + client.email() + "):"), row,
                Ui.wrapped("It won't be shown again. The reset is recorded in the audit log.", Styles.TEXT_MUTED)));
    }

    private void closeAccount(User client, Money balance) {
        workspace.window().dialogs().confirm("Close " + client.fullName() + "'s accounts?",
                "This permanently deletes their profile, accounts and history. Remaining balance: "
                        + balance.format() + ". Their past transfers stay on other clients' statements.",
                "Close accounts", true, h -> {
                    try {
                        ctx.admin().deleteClient(admin, client.id());
                        h.close();
                        workspace.window().notifier().success("Closed " + client.email() + ".");
                        load("", null);
                    } catch (RuntimeException e) {
                        h.error(Async.describe(e));
                    }
                });
    }

    /** Opening a client account, the 2022 two-step form folded into one dialog. */
    public static void openCreateDialog(Workspace workspace) {
        AppContext ctx = workspace.context();
        TextField name = new TextField();
        name.setPromptText("First and last name");
        TextField email = new TextField();
        email.setPromptText("name@example.com");
        PasswordTextField password = new PasswordTextField();
        PasswordTextField confirm = new PasswordTextField();
        ComboBox<Gender> gender = new ComboBox<>();
        gender.getItems().setAll(Gender.values());
        gender.setPromptText("Select");
        DatePicker birth = Inputs.date(null);
        TextField phone = new TextField();
        TextField facebook = new TextField();
        facebook.setPromptText("facebook.com/...");
        TextField address = new TextField();
        CustomTextField checking = Inputs.money();
        checking.setText(AdminService.DEFAULT_OPENING_CHECKING.plain());
        CustomTextField savings = Inputs.money();
        savings.setText(AdminService.DEFAULT_OPENING_SAVINGS.plain());
        CheckBox agree = new CheckBox("The client agrees to the terms of usage and privacy policy");

        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(12);
        form.add(Ui.label("Personal information", Styles.TEXT_BOLD), 0, 0, 2, 1);
        form.add(Ui.field("Full name", name), 0, 1);
        form.add(Ui.field("Email", email), 1, 1);
        form.add(Ui.field("Password", password, "At least " + Validation.MIN_PASSWORD_LENGTH
                + " characters, letters and numbers"), 0, 2);
        form.add(Ui.field("Confirm password", confirm), 1, 2);
        form.add(Ui.field("Gender", gender), 0, 3);
        form.add(Ui.field("Date of birth", birth), 1, 3);
        form.add(Ui.label("Contact", Styles.TEXT_BOLD), 0, 4, 2, 1);
        form.add(Ui.field("Phone", phone), 0, 5);
        form.add(Ui.field("Facebook", facebook), 1, 5);
        form.add(Ui.field("Home address", address), 0, 6, 2, 1);
        form.add(Ui.label("Opening balances", Styles.TEXT_BOLD), 0, 7, 2, 1);
        form.add(Ui.field("Checking", checking), 0, 8);
        form.add(Ui.field("Savings", savings), 1, 8);
        form.add(agree, 0, 9, 2, 1);
        ColumnConstraints col = new ColumnConstraints();
        col.setPercentWidth(50);
        form.getColumnConstraints().addAll(col, col);

        workspace.window().dialogs().show("Open a client account", form, 640, h -> {
            Button cancel = Ui.secondary("Cancel", null);
            cancel.setOnAction(e -> h.close());
            Button create = Ui.primary("Create account", Material2OutlinedMZ.PERSON_ADD);
            create.setDefaultButton(true);
            create.setOnAction(e -> {
                if (!password.getPassword().equals(confirm.getPassword())) {
                    h.error("Passwords don't match.");
                    return;
                }
                if (!agree.isSelected()) {
                    h.error("The client must agree to the terms to open an account.");
                    return;
                }
                var request = new AdminService.NewClient(name.getText(), email.getText(), password.getPassword(),
                        gender.getValue(), birth.getValue(), phone.getText(), facebook.getText(), address.getText(),
                        Inputs.amountOf(checking).orElse(Money.ZERO), Inputs.amountOf(savings).orElse(Money.ZERO));
                Async.run(create, () -> ctx.admin().createClient(workspace.user(), request), created -> {
                    h.close();
                    workspace.window().notifier().success("Opened accounts for " + created.fullName() + ".");
                    workspace.navigate(Workspace.CLIENTS, new ClientsPage(workspace, created.email()));
                }, h::error);
            });
            return new Node[]{cancel, create};
        });
    }

    private static String dash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
