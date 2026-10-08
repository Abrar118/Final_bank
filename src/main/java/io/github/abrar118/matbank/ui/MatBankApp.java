package io.github.abrar118.matbank.ui;

import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.AppPaths;
import io.github.abrar118.matbank.demo.DemoSeeder;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;

import java.time.Clock;

/** JavaFX entry point. Opens the database in {@link #init()}, then shows the welcome screen. */
public class MatBankApp extends Application {

    private AppContext context;
    private Throwable startupError;

    @Override
    public void init() {
        try {
            context = AppContext.open(AppPaths.detect(), Clock.systemDefaultZone());
        } catch (RuntimeException e) {
            startupError = e;
        }
    }

    @Override
    public void start(Stage stage) {
        if (startupError != null) {
            showStartupError(startupError);
            return;
        }
        Ui.setHostServices(getHostServices());
        stage.setTitle("MAT Bank");
        stage.getIcons().add(Ui.image("logo.png"));
        stage.setMinWidth(1100);
        stage.setMinHeight(720);

        AppWindow window = new AppWindow(context, stage);
        window.showWelcome();
        stage.show();

        boolean demo = Boolean.parseBoolean(System.getProperty("matbank.demo", "true"));
        if (demo && DemoSeeder.isEmpty(context)) {
            window.notifier().info("Setting up a demo bank for your first visit...");
            Async.run(() -> new DemoSeeder(context).seedIfEmpty(), seeded -> {
                window.setReady(true);
                window.notifier().success("Demo bank ready. Pick a demo account on the sign-in screen.");
            }, error -> {
                window.setReady(true);
                window.notifier().error("Could not create demo data: " + error);
            });
        } else {
            window.setReady(true);
        }

        context.startScheduler(summary -> Platform.runLater(() -> window.onScheduledPayments(summary)));
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
    }

    private static void showStartupError(Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("MAT Bank");
        alert.setHeaderText("MAT Bank could not open its database");
        TextArea details = new TextArea(String.valueOf(error.getMessage()));
        details.setEditable(false);
        details.setWrapText(true);
        alert.getDialogPane().setContent(details);
        alert.showAndWait();
        Platform.exit();
    }
}
