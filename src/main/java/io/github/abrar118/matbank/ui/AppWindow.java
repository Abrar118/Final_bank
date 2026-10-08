package io.github.abrar118.matbank.ui;

import atlantafx.base.controls.ModalPane;
import atlantafx.base.util.Animations;
import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.RecurringPaymentService;
import io.github.abrar118.matbank.ui.auth.LoginView;
import io.github.abrar118.matbank.ui.auth.WelcomeView;
import io.github.abrar118.matbank.ui.common.AboutPage;
import io.github.abrar118.matbank.ui.common.HelpCenterPage;
import io.github.abrar118.matbank.ui.common.StandalonePage;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.util.Duration;

/**
 * The single application window. Screens swap inside it; the 2022 app opened and closed a new Stage for every
 * screen. It also owns the shared dialog layer, toasts and avatar cache.
 */
public final class AppWindow {

    private final AppContext context;
    private final Stage stage;
    private final Scene scene;
    private final StackPane content = new StackPane();
    private final Notifier notifier = new Notifier();
    private final Dialogs dialogs;
    private final Avatars avatars;
    private final BooleanProperty ready = new SimpleBooleanProperty(false);
    private Workspace workspace;

    public AppWindow(AppContext context, Stage stage) {
        this.context = context;
        this.stage = stage;
        ModalPane modal = new ModalPane();
        modal.setPersistent(false);
        this.dialogs = new Dialogs(modal);
        this.avatars = new Avatars(context.profiles());
        content.getStyleClass().add("app-content");
        StackPane root = new StackPane(content, modal, notifier.layer());
        root.getStyleClass().add("app-root");
        this.scene = new Scene(root, 1280, 820);
        Theme.apply(Theme.saved(), scene);
        stage.setScene(scene);
    }

    public AppContext context() {
        return context;
    }

    public Stage stage() {
        return stage;
    }

    public Scene scene() {
        return scene;
    }

    public Notifier notifier() {
        return notifier;
    }

    public Dialogs dialogs() {
        return dialogs;
    }

    public Avatars avatars() {
        return avatars;
    }

    public Workspace workspace() {
        return workspace;
    }

    /** False while the first-run demo data is still being generated. */
    public ReadOnlyBooleanProperty readyProperty() {
        return ready;
    }

    public void setReady(boolean value) {
        ready.set(value);
    }

    public void toggleTheme() {
        Theme.apply(Theme.current().toggled(), scene);
    }

    public void showWelcome() {
        workspace = null;
        setContent(new WelcomeView(this).build());
    }

    public void showLogin() {
        workspace = null;
        setContent(new LoginView(this).build());
    }

    /** Help center for visitors who are not signed in. */
    public void showVisitorHelp(Runnable back) {
        setContent(new StandalonePage("Help center", new HelpCenterPage(this, null).view(), back).build());
    }

    public void showVisitorAbout(Runnable back) {
        setContent(new StandalonePage("About MAT Bank", new AboutPage(this).view(), back).build());
    }

    public void signIn(User user) {
        workspace = new Workspace(this, user);
        setContent(workspace.view());
        notifier.success("Welcome back, " + user.firstName() + "!");
    }

    public void signOut() {
        workspace = null;
        showLogin();
        notifier.info("You have been signed out.");
    }

    /** Called (on the FX thread) after the background scheduler paid or skipped scheduled payments. */
    public void onScheduledPayments(RecurringPaymentService.RunSummary summary) {
        if (workspace == null || workspace.user().isAdmin()) {
            return;
        }
        workspace.refresh();
        String text = summary.paid() + " scheduled payment" + (summary.paid() == 1 ? "" : "s") + " processed"
                + (summary.skipped() > 0 ? ", " + summary.skipped() + " skipped" : "") + ".";
        notifier.info(text);
    }

    private void setContent(Node node) {
        content.getChildren().setAll(node);
        Animations.fadeIn(node, Duration.millis(220)).playFromStart();
    }
}
