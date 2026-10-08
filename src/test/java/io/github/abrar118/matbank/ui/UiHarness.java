package io.github.abrar118.matbank.ui;

import io.github.abrar118.matbank.AppContext;
import io.github.abrar118.matbank.db.Database;
import io.github.abrar118.matbank.db.Migrator;
import io.github.abrar118.matbank.demo.DemoSeeder;
import io.github.abrar118.matbank.domain.Role;
import io.github.abrar118.matbank.domain.User;
import io.github.abrar118.matbank.service.AuthService;
import io.github.abrar118.matbank.service.ExchangeRateService;
import io.github.abrar118.matbank.service.PasswordHasher;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Starts the real UI on a demo database for smoke tests and screenshots. Needs a display (Xvfb works).
 */
final class UiHarness {

    private static boolean toolkitStarted;

    final AppContext ctx;
    final AppWindow window;

    private UiHarness(AppContext ctx, AppWindow window) {
        this.ctx = ctx;
        this.window = window;
    }

    static synchronized UiHarness start(Path dir, int width, int height) throws Exception {
        Clock clock = Clock.systemDefaultZone();
        Database db = new Database(dir.resolve("ui.db"));
        new Migrator(db, clock).migrate();
        var rates = new ExchangeRateService(() -> {
            throw new IOException("offline");
        }, null, clock);
        AppContext ctx = new AppContext(db, clock, new PasswordHasher(4), rates);
        new DemoSeeder(ctx).seedIfEmpty();

        if (!toolkitStarted) {
            CountDownLatch started = new CountDownLatch(1);
            Platform.startup(started::countDown);
            started.await(10, TimeUnit.SECONDS);
            Platform.setImplicitExit(false);
            toolkitStarted = true;
        }
        AppWindow window = fx(() -> {
            Stage stage = new Stage();
            stage.setWidth(width);
            stage.setHeight(height);
            AppWindow w = new AppWindow(ctx, stage);
            w.setReady(true);
            stage.show();
            return w;
        });
        return new UiHarness(ctx, window);
    }

    User signIn(String email, String password, Role role, String pin) throws Exception {
        var result = ctx.auth().login(email, password, role, pin);
        User user = ((AuthService.LoginResult.Success) result).user();
        fx(() -> {
            window.signIn(user);
            return null;
        });
        settle();
        return user;
    }

    /** Lets animations, image loading and background work finish. */
    void settle() throws Exception {
        for (int i = 0; i < 4; i++) {
            Thread.sleep(150);
            fx(() -> null);
        }
    }

    void run(Runnable action) throws Exception {
        fx(() -> {
            action.run();
            return null;
        });
        settle();
    }

    WritableImage snapshot() throws Exception {
        return fx(() -> window.scene().snapshot(null));
    }

    void screenshot(Path file) throws Exception {
        WritableImage image = snapshot();
        Files.createDirectories(file.getParent());
        ImageIO.write(SwingFXUtils.fromFXImage(image, null), "png", file.toFile());
    }

    void close() throws Exception {
        fx(() -> {
            window.stage().close();
            return null;
        });
        ctx.close();
    }

    static <T> T fx(java.util.concurrent.Callable<T> work) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return work.call();
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(work.call());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(30, TimeUnit.SECONDS)) {
            throw new IllegalStateException("FX thread did not respond");
        }
        if (error.get() != null) {
            throw new IllegalStateException("UI action failed", error.get());
        }
        return result.get();
    }
}
