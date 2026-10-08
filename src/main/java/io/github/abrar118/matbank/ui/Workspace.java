package io.github.abrar118.matbank.ui;

import atlantafx.base.controls.CustomTextField;
import atlantafx.base.theme.Styles;
import atlantafx.base.util.Animations;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.ui.admin.AdminOverviewPage;
import io.github.abrar118.matbank.ui.admin.AuditLogPage;
import io.github.abrar118.matbank.ui.admin.ClientsPage;
import io.github.abrar118.matbank.ui.admin.InboxPage;
import io.github.abrar118.matbank.ui.admin.LoginActivityPage;
import io.github.abrar118.matbank.ui.client.CurrencyPage;
import io.github.abrar118.matbank.ui.client.DashboardPage;
import io.github.abrar118.matbank.ui.client.DepositPage;
import io.github.abrar118.matbank.ui.client.HistoryPage;
import io.github.abrar118.matbank.ui.client.ScheduledPaymentsPage;
import io.github.abrar118.matbank.ui.client.StatementsPage;
import io.github.abrar118.matbank.ui.client.TransferPage;
import io.github.abrar118.matbank.ui.common.AboutPage;
import io.github.abrar118.matbank.ui.common.HelpCenterPage;
import io.github.abrar118.matbank.ui.common.ProfilePage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.material2.Material2OutlinedAL;
import org.kordamp.ikonli.material2.Material2OutlinedMZ;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * The signed-in shell: sidebar navigation, a top bar with search and theme toggle, and the current page.
 * Clients and admins get different menus, as in 2022, but in one window instead of a hover-out menu.
 */
public final class Workspace {

    /** Page identifiers used for navigation. */
    public static final String DASHBOARD = "dashboard";
    public static final String TRANSFER = "transfer";
    public static final String DEPOSIT = "deposit";
    public static final String HISTORY = "history";
    public static final String SCHEDULED = "scheduled";
    public static final String STATEMENTS = "statements";
    public static final String CURRENCY = "currency";
    public static final String HELP = "help";
    public static final String PROFILE = "profile";
    public static final String ABOUT = "about";
    public static final String OVERVIEW = "overview";
    public static final String CLIENTS = "clients";
    public static final String INBOX = "inbox";
    public static final String LOGINS = "logins";
    public static final String AUDIT = "audit";

    private record NavItem(String id, String label, Ikon icon, Supplier<Page> factory) {
    }

    private final AppWindow window;
    private User user;
    private final Map<String, NavItem> items = new LinkedHashMap<>();
    private final Map<String, ToggleButton> buttons = new LinkedHashMap<>();
    private final ToggleGroup navGroup = new ToggleGroup();
    private final BorderPane layout = new BorderPane();
    private final StackPane center = new StackPane();
    private final Label title = Ui.label("", Styles.TITLE_3);
    private final CustomTextField search = new CustomTextField();
    private final VBox sidebarFooter = new VBox();
    private Label inboxBadge;
    private String current;

    public Workspace(AppWindow window, User user) {
        this.window = window;
        this.user = user;
        if (user.isAdmin()) {
            add(OVERVIEW, "Overview", Material2OutlinedAL.DASHBOARD, () -> new AdminOverviewPage(this));
            add(CLIENTS, "Clients", Material2OutlinedMZ.PEOPLE, () -> new ClientsPage(this, null));
            add(INBOX, "Inbox", Material2OutlinedAL.INBOX, () -> new InboxPage(this));
            add(LOGINS, "Sign-in activity", Material2OutlinedAL.LOGIN, () -> new LoginActivityPage(this));
            add(AUDIT, "Audit log", Material2OutlinedAL.FACT_CHECK, () -> new AuditLogPage(this));
        } else {
            add(DASHBOARD, "Dashboard", Material2OutlinedAL.DASHBOARD, () -> new DashboardPage(this));
            add(TRANSFER, "Send money", Material2OutlinedMZ.SEND, () -> new TransferPage(this));
            add(DEPOSIT, "Deposit", Material2OutlinedAL.ACCOUNT_BALANCE_WALLET, () -> new DepositPage(this));
            add(HISTORY, "History", Material2OutlinedAL.HISTORY, () -> new HistoryPage(this, null));
            add(SCHEDULED, "Scheduled payments", Material2OutlinedMZ.REPEAT, () -> new ScheduledPaymentsPage(this));
            add(STATEMENTS, "Statements", Material2OutlinedMZ.PICTURE_AS_PDF, () -> new StatementsPage(this));
            add(CURRENCY, "Currency", Material2OutlinedMZ.MONETIZATION_ON, () -> new CurrencyPage(this));
            add(HELP, "Help center", Material2OutlinedMZ.SUPPORT_AGENT, () -> new HelpCenterPage(window, this.user));
        }
        add(PROFILE, "Profile", Material2OutlinedMZ.PERSON, () -> new ProfilePage(this));
        add(ABOUT, "About", Material2OutlinedAL.INFO, () -> new AboutPage(window));

        BorderPane main = new BorderPane(center);
        main.setTop(topBar());
        layout.setLeft(sidebar());
        layout.setCenter(main);
        layout.getStyleClass().add("workspace");
        navigate(user.isAdmin() ? OVERVIEW : DASHBOARD);
    }

    public Node view() {
        return layout;
    }

    public User user() {
        return user;
    }

    public AppWindow window() {
        return window;
    }

    public AppContext context() {
        return window.context();
    }

    public void navigate(String id) {
        NavItem item = items.get(id);
        show(item, item.factory().get());
    }

    /** Opens a page that needs arguments (e.g. history pre-filtered by a search). */
    public void navigate(String id, Page page) {
        show(items.get(id), page);
    }

    /** Rebuilds the current page, e.g. after a background job changed balances. */
    public void refresh() {
        if (current != null) {
            navigate(current);
        }
    }

    /** Reloads the signed-in user after a profile change and redraws the sidebar. */
    public void reloadUser() {
        user = context().profiles().get(user.id());
        window.avatars().invalidate(user.id());
        renderFooter();
    }

    public void updateInboxBadge() {
        if (inboxBadge == null) {
            return;
        }
        long unread = context().support().unreadCount();
        inboxBadge.setText(String.valueOf(unread));
        inboxBadge.setVisible(unread > 0);
    }

    private void add(String id, String label, Ikon icon, Supplier<Page> factory) {
        items.put(id, new NavItem(id, label, icon, factory));
    }

    private void show(NavItem item, Page page) {
        current = item.id();
        title.setText(item.label());
        ToggleButton button = buttons.get(item.id());
        if (button != null) {
            button.setSelected(true);
        }
        Node view;
        try {
            view = page.view();
        } catch (RuntimeException e) {
            view = Ui.empty(Material2OutlinedMZ.WARNING, "This page could not be loaded", Async.describe(e));
        }
        center.getChildren().setAll(view);
        Animations.fadeIn(view, Duration.millis(180)).playFromStart();
        updateInboxBadge();
    }

    private Node sidebar() {
        HBox brand = new HBox(10, Ui.imageView("logo.png", 34), Ui.wordmark(20));
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setPadding(new Insets(4, 8, 16, 8));

        VBox nav = new VBox(4);
        List<String> secondary = List.of(PROFILE, ABOUT, HELP);
        boolean dividerAdded = false;
        for (NavItem item : items.values()) {
            if (secondary.contains(item.id()) && !dividerAdded) {
                Label section = Ui.label("ACCOUNT", "nav-section");
                nav.getChildren().add(section);
                dividerAdded = true;
            }
            ToggleButton button = new ToggleButton(item.label(), Ui.icon(item.icon(), 18));
            button.setToggleGroup(navGroup);
            button.getStyleClass().add("nav-button");
            button.setMaxWidth(Double.MAX_VALUE);
            button.setAlignment(Pos.CENTER_LEFT);
            button.setOnAction(e -> {
                if (!button.isSelected()) {
                    button.setSelected(true);
                    return;
                }
                navigate(item.id());
            });
            if (item.id().equals(INBOX)) {
                inboxBadge = Ui.label("0", "nav-badge");
                inboxBadge.setVisible(false);
                HBox graphic = new HBox(10, Ui.icon(item.icon(), 18));
                button.setGraphic(graphic);
                button.setText(null);
                graphic.getChildren().addAll(new Label(item.label()), Ui.spacer(), inboxBadge);
                graphic.setAlignment(Pos.CENTER_LEFT);
                graphic.setMaxWidth(Double.MAX_VALUE);
                button.setMaxWidth(Double.MAX_VALUE);
                graphic.prefWidthProperty().bind(button.widthProperty().subtract(32));
            }
            buttons.put(item.id(), button);
            nav.getChildren().add(button);
        }

        renderFooter();
        VBox sidebar = new VBox(brand, nav, Ui.spacer(), sidebarFooter);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new Insets(20, 14, 16, 14));
        sidebar.setPrefWidth(244);
        sidebar.setMinWidth(244);
        return sidebar;
    }

    private void renderFooter() {
        Button logout = Ui.iconButton(Material2OutlinedAL.EXIT_TO_APP, "Sign out");
        logout.setOnAction(e -> window.dialogs().confirm("Sign out?", "You'll return to the sign-in screen.",
                "Sign out", false, h -> {
                    h.close();
                    window.signOut();
                }));
        Label name = Ui.label(user.fullName(), Styles.TEXT_BOLD);
        Label role = Ui.label(user.isAdmin() ? "Administrator" : "Client", Styles.TEXT_MUTED, Styles.TEXT_SMALL);
        VBox who = new VBox(2, name, role);
        who.setMinWidth(0);
        HBox.setHgrow(who, javafx.scene.layout.Priority.ALWAYS);
        HBox footer = new HBox(10, window.avatars().of(user, 36), who, logout);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("sidebar-footer");
        footer.setPadding(new Insets(12, 6, 4, 6));
        footer.setOnMouseClicked(e -> {
            if (e.getTarget() != logout && !logout.isHover()) {
                navigate(PROFILE);
            }
        });
        sidebarFooter.getChildren().setAll(footer);
    }

    private Node topBar() {
        search.setPromptText(user.isAdmin() ? "Search clients by name or email" : "Search your transactions");
        search.setLeft(Ui.icon(Material2OutlinedMZ.SEARCH, 16));
        search.setPrefWidth(340);
        search.setOnAction(e -> runSearch());

        Button theme = Ui.themeToggle(window);
        Button help = Ui.iconButton(Material2OutlinedAL.HELP_OUTLINE, "Help center");
        help.setOnAction(e -> navigate(user.isAdmin() ? INBOX : HELP));
        help.setTooltip(new Tooltip(user.isAdmin() ? "Inbox" : "Help center"));

        HBox bar = new HBox(12, title, Ui.spacer(), search, help, theme);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("top-bar");
        bar.setPadding(new Insets(14, 24, 14, 28));
        return bar;
    }

    private void runSearch() {
        String text = search.getText() == null ? "" : search.getText().strip();
        if (user.isAdmin()) {
            navigate(CLIENTS, new ClientsPage(this, text));
        } else {
            navigate(HISTORY, new HistoryPage(this, text));
        }
    }
}
