package io.github.abrar118.matbank;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Where MAT Bank keeps its data. The 2022 build wrote into {@code ~/Music/Data}; now each OS gets its usual
 * application-data folder, overridable with {@code -Dmatbank.home=...} or the {@code MATBANK_HOME} variable.
 */
public record AppPaths(Path home) {

    public static AppPaths detect() {
        String override = System.getProperty("matbank.home");
        if (override == null || override.isBlank()) {
            override = System.getenv("MATBANK_HOME");
        }
        if (override != null && !override.isBlank()) {
            return new AppPaths(Path.of(override).toAbsolutePath());
        }
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        Path userHome = Path.of(System.getProperty("user.home"));
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            Path base = appData == null ? userHome.resolve("AppData").resolve("Roaming") : Path.of(appData);
            return new AppPaths(base.resolve("MAT Bank"));
        }
        if (os.contains("mac")) {
            return new AppPaths(userHome.resolve("Library").resolve("Application Support").resolve("MAT Bank"));
        }
        String xdg = System.getenv("XDG_DATA_HOME");
        Path base = xdg == null || xdg.isBlank() ? userHome.resolve(".local").resolve("share") : Path.of(xdg);
        return new AppPaths(base.resolve("mat-bank"));
    }

    public Path database() {
        return home.resolve("matbank.db");
    }

    public Path exchangeRateCache() {
        return home.resolve("cache").resolve("exchange-rates.json");
    }

    public void create() {
        try {
            Files.createDirectories(home.resolve("cache"));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create data folder " + home, e);
        }
    }
}
