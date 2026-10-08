package io.github.abrar118.matbank.ui;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.css.PseudoClass;
import javafx.scene.Scene;

import java.util.Objects;
import java.util.prefs.Preferences;

/** Light and dark themes (AtlantaFX Primer plus MAT Bank's own stylesheet). The choice is remembered. */
public enum Theme {
    LIGHT,
    DARK;

    private static final PseudoClass DARK_CLASS = PseudoClass.getPseudoClass("dark");
    private static final String PREF_KEY = "theme";
    private static final ObjectProperty<Theme> CURRENT = new SimpleObjectProperty<>(LIGHT);

    public static ObjectProperty<Theme> currentProperty() {
        return CURRENT;
    }

    public static Theme current() {
        return CURRENT.get();
    }

    public static Theme saved() {
        try {
            return Theme.valueOf(prefs().get(PREF_KEY, LIGHT.name()));
        } catch (RuntimeException e) {
            return LIGHT;
        }
    }

    /** Applies the theme to the app and to {@code scene}, and remembers it. */
    public static void apply(Theme theme, Scene scene) {
        Application.setUserAgentStylesheet(theme == DARK
                ? new PrimerDark().getUserAgentStylesheet()
                : new PrimerLight().getUserAgentStylesheet());
        String css = Objects.requireNonNull(Theme.class.getResource("/io/github/abrar118/matbank/css/app.css"))
                .toExternalForm();
        if (scene != null) {
            if (!scene.getStylesheets().contains(css)) {
                scene.getStylesheets().add(css);
            }
            if (scene.getRoot() != null) {
                scene.getRoot().pseudoClassStateChanged(DARK_CLASS, theme == DARK);
            }
        }
        CURRENT.set(theme);
        try {
            prefs().put(PREF_KEY, theme.name());
        } catch (RuntimeException ignored) {
            // Preferences can be unavailable (e.g. read-only home); the theme still applies for this session.
        }
    }

    public Theme toggled() {
        return this == DARK ? LIGHT : DARK;
    }

    private static Preferences prefs() {
        return Preferences.userRoot().node("io/github/abrar118/matbank");
    }
}
