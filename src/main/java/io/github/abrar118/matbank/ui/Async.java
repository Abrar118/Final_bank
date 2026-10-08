package io.github.abrar118.matbank.ui;

import io.github.abrar118.matbank.db.DataAccessException;
import io.github.abrar118.matbank.service.BankException;
import javafx.application.Platform;
import javafx.scene.Node;

import java.io.UncheckedIOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Runs slow work (password hashing, PDF rendering, network calls) on a virtual thread and hands the result back
 * on the JavaFX thread, so the window never freezes.
 */
public final class Async {

    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final System.Logger LOG = System.getLogger("matbank.ui");

    private Async() {
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<String> onError) {
        run(null, work, onSuccess, onError);
    }

    /**
     * @param busy a control to disable while the work runs (may be {@code null})
     */
    public static <T> void run(Node busy, Callable<T> work, Consumer<T> onSuccess, Consumer<String> onError) {
        if (busy != null) {
            busy.setDisable(true);
        }
        EXECUTOR.submit(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> {
                    if (busy != null) {
                        busy.setDisable(false);
                    }
                    onSuccess.accept(result);
                });
            } catch (Throwable t) {
                String message = describe(t);
                Platform.runLater(() -> {
                    if (busy != null) {
                        busy.setDisable(false);
                    }
                    onError.accept(message);
                });
            }
        });
    }

    /** Turns any failure into a sentence for the user; unexpected ones are logged. */
    public static String describe(Throwable t) {
        return switch (t) {
            case BankException e -> e.getMessage();
            case IllegalArgumentException e -> e.getMessage();
            case UncheckedIOException e -> e.getMessage();
            case DataAccessException e -> {
                LOG.log(System.Logger.Level.ERROR, "Database error", e);
                yield "Something went wrong saving your data. Please try again.";
            }
            default -> {
                LOG.log(System.Logger.Level.ERROR, "Unexpected error", t);
                yield "Something unexpected went wrong: " + t.getClass().getSimpleName();
            }
        };
    }
}
